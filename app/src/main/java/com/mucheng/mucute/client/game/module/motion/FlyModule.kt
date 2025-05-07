package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.3f, 0.05f..1.0f) // Горизонтальная скорость
    private var verticalMultiplier by floatValue("verticalMultiplier", 0.8f, 0.5f..2.0f) // Множитель вертикальной скорости
    private var canFly = false // Флаг активации способностей

    // Собственная функция для преобразования градусов в радианы
    private fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)

    // Пакет для включения полета локально
    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                arrayOf(
                    Ability.BUILD,
                    Ability.MINE,
                    Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS,
                    Ability.ATTACK_PLAYERS,
                    Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS,
                    Ability.MAY_FLY,
                    Ability.FLY_SPEED,
                    Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
            flySpeed = this@FlyModule.flySpeed
        })
    }

    // Пакет для отключения полета
    private val disableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                arrayOf(
                    Ability.BUILD,
                    Ability.MINE,
                    Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS,
                    Ability.ATTACK_PLAYERS,
                    Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS,
                    Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
            flySpeed = 0.0f
        })
    }

    // Пакет для отключения FLY_SPEED при ходьбе
    private val walkingAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                arrayOf(
                    Ability.BUILD,
                    Ability.MINE,
                    Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS,
                    Ability.ATTACK_PLAYERS,
                    Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS,
                    Ability.MAY_FLY,
                    Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
            flySpeed = 0.0f
        })
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Блокируем запросы на способности полета
        if (packet is RequestAbilityPacket && packet.ability == Ability.FLYING) {
            interceptablePacket.intercept()
            return
        }

        // Обрабатываем ввод игрока
        if (packet is PlayerAuthInputPacket) {
            // Включаем способности полета при активации модуля
            if (!canFly && isEnabled) {
                enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(enableFlyAbilitiesPacket)
                canFly = true
            } else if (canFly && !isEnabled) {
                disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(disableFlyAbilitiesPacket)
                canFly = false
            }

            if (isEnabled) {
                // Перехватываем START_FLYING, STOP_FLYING и FLYING
                if (packet.inputData.contains(PlayerAuthInputData.START_FLYING) ||
                    packet.inputData.contains(PlayerAuthInputData.STOP_FLYING) ||
                    packet.inputData.contains(PlayerAuthInputData.FLYING)) {
                    interceptablePacket.intercept()
                }

                // Проверяем, активен ли полет
                val isFlying = packet.inputData.contains(PlayerAuthInputData.JUMPING) ||
                        packet.inputData.contains(PlayerAuthInputData.SNEAKING)

                // Управляем вертикальным движением
                var verticalMotion = 0f // Без гравитации по умолчанию
                if (isFlying) {
                    if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                        verticalMotion = flySpeed * verticalMultiplier // Взлет
                    } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                        verticalMotion = -flySpeed * verticalMultiplier // Спуск
                    }
                }

                // Получаем горизонтальное движение
                val inputMotion = packet.motion?.let {
                    Vector3f.from(it.getX(), 0f, it.getY())
                } ?: Vector3f.ZERO

                // Получаем угол поворота (yaw)
                val yaw = packet.rotation?.y?.toDouble()?.let { toRadians(it) } ?: 0.0
                // Преобразуем движение в направлении взгляда
                val horizontalMotion = if (inputMotion != Vector3f.ZERO && isFlying) {
                    val speed = flySpeed.toDouble() * 0.9 // Горизонтальная скорость
                    Vector3f.from(
                        ((-sin(yaw) * inputMotion.getZ().toDouble() + cos(yaw) * inputMotion.getX().toDouble()) * speed).toFloat(),
                        0f,
                        ((cos(yaw) * inputMotion.getZ().toDouble() + sin(yaw) * inputMotion.getX().toDouble()) * speed).toFloat()
                    )
                } else {
                    Vector3f.ZERO
                }

                // Комбинируем горизонтальное и вертикальное движение
                val combinedMotion = Vector3f.from(
                    horizontalMotion.getX(),
                    verticalMotion,
                    horizontalMotion.getZ()
                )

                // Отправляем движение клиенту только при активности
                if (combinedMotion.getX().toDouble().absoluteValue > 0.01 ||
                    combinedMotion.getZ().toDouble().absoluteValue > 0.01 ||
                    verticalMotion.absoluteValue > 0.01) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = combinedMotion
                    }
                    session.clientBound(motionPacket)
                } else if (!isFlying && inputMotion == Vector3f.ZERO) {
                    // Мгновенная остановка
                    val stopMotionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = Vector3f.ZERO
                    }
                    session.clientBound(stopMotionPacket)
                }

                // Управляем FLY_SPEED для ходьбы
                if (!isFlying && inputMotion == Vector3f.ZERO) {
                    walkingAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                    session.clientBound(walkingAbilitiesPacket)
                } else {
                    enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                    session.clientBound(enableFlyAbilitiesPacket)
                }

                // Синхронизируем позицию с сервером
                val playerPosition = packet.position?.let { Vector3f.from(it.getX(), it.getY(), it.getZ()) } ?: Vector3f.ZERO
                if (playerPosition != Vector3f.ZERO) {
                    val movePacket = MovePlayerPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        position = playerPosition
                        rotation = packet.rotation?.let { Vector3f.from(it.getX(), it.getY(), it.getZ()) } ?: Vector3f.ZERO
                        mode = MovePlayerPacket.Mode.NORMAL
                        tick = packet.tick
                    }
                    session.serverBound(movePacket)
                }
            }
        }
    }
}
