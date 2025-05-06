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

    private var flySpeed by floatValue("flySpeed", 0.2f, 0.05f..1.0f) // Базовая скорость (настроить для креатива)
    private var isFlying = false // Флаг активного полета
    private var lastMotion = Vector3f.ZERO // Для сглаживания движения

    // Собственная функция для преобразования градусов в радианы
    private fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)

    // Собственная функция lerp для Vector3f
    private fun Vector3f.lerp(other: Vector3f, t: Float): Vector3f {
        val tInv = 1.0f - t
        return Vector3f.from(
            getX() * tInv + other.getX() * t,
            getY() * tInv + other.getY() * t,
            getZ() * tInv + other.getZ() * t
        )
    }

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

    // Пакет для полного отключения полета
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
            flySpeed = 0.0f // Явно сбрасываем скорость полета
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
            // Перехватываем START_FLYING и STOP_FLYING
            if (isEnabled) {
                if (packet.inputData.contains(PlayerAuthInputData.START_FLYING)) {
                    interceptablePacket.intercept() // Перехватываем пакет
                    if (!isFlying) {
                        enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                        session.clientBound(enableFlyAbilitiesPacket)
                        isFlying = true
                    }
                    return
                } else if (packet.inputData.contains(PlayerAuthInputData.STOP_FLYING)) {
                    interceptablePacket.intercept() // Перехватываем пакет
                    if (isFlying) {
                        disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                        session.clientBound(disableFlyAbilitiesPacket)
                        // Сбрасываем движение
                        val stopMotionPacket = SetEntityMotionPacket().apply {
                            runtimeEntityId = session.localPlayer.runtimeEntityId
                            motion = Vector3f.from(0f, -0.08f, 0f) // Гравитация
                        }
                        session.clientBound(stopMotionPacket)
                        isFlying = false
                        lastMotion = Vector3f.ZERO
                    }
                    return
                }
            }

            // Отключаем способности, если модуль выключен
            if (!isEnabled && isFlying) {
                disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(disableFlyAbilitiesPacket)
                val stopMotionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = session.localPlayer.runtimeEntityId
                    motion = Vector3f.from(0f, -0.08f, 0f) // Гравитация
                }
                session.clientBound(stopMotionPacket)
                isFlying = false
                lastMotion = Vector3f.ZERO
            }

            // Обрабатываем движение только при активном полете
            if (isEnabled && isFlying) {
                // Управляем движением локально
                var verticalMotion = 0f
                if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMotion = flySpeed * 1.3f // Для креативного подъема
                } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                    verticalMotion = -flySpeed * 1.3f // Для спуска
                }

                // Получаем горизонтальное движение из packet.motion
                val inputMotion = packet.motion?.let {
                    Vector3f.from(it.getX(), 0f, it.getY()) // y в motion соответствует z в 3D
                } ?: Vector3f.ZERO

                // Получаем угол поворота (yaw) из rotation
                val yaw = packet.rotation?.y?.toDouble()?.let { toRadians(it) } ?: 0.0
                // Преобразуем движение в направлении взгляда
                val horizontalMotion = if (inputMotion != Vector3f.ZERO) {
                    val speed = flySpeed.toDouble() * 0.8 // Уменьшен множитель для плавности
                    // Вращаем вектор движения в соответствии с yaw
                    val newMotion = Vector3f.from(
                        ((-sin(yaw) * inputMotion.getZ().toDouble() + cos(yaw) * inputMotion.getX().toDouble()) * speed).toFloat(),
                        0f,
                        ((cos(yaw) * inputMotion.getZ().toDouble() + sin(yaw) * inputMotion.getX().toDouble()) * speed).toFloat()
                    )
                    // Сглаживание: комбинируем новое движение с предыдущим
                    lastMotion.lerp(newMotion, 0.5f)
                } else {
                    lastMotion.lerp(Vector3f.ZERO, 0.5f) // Плавное затухание
                }

                // Комбинируем горизонтальное и вертикальное движение
                val combinedMotion = Vector3f.from(
                    horizontalMotion.getX(),
                    verticalMotion,
                    horizontalMotion.getZ()
                )

                // Отправляем SetEntityMotionPacket
                val motionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = session.localPlayer.runtimeEntityId
                    motion = combinedMotion
                }
                session.clientBound(motionPacket)
                lastMotion = horizontalMotion // Обновляем последнее движение

                // Синхронизируем позицию с сервером через MovePlayerPacket
                val playerPosition = packet.position?.let { Vector3f.from(it.getX(), it.getY(), it.getZ()) } ?: Vector3f.ZERO
                if (playerPosition != Vector3f.ZERO) {
                    val movePacket = MovePlayerPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        position = playerPosition
                        rotation = packet.rotation?.let { Vector3f.from(it.getX(), it.getY(), it.getZ()) } ?: Vector3f.ZERO
                        mode = MovePlayerPacket.Mode.NORMAL
                        setOnGround(false) // Игрок в полете
                        tick = packet.tick
                    }
                    session.serverBound(movePacket) // Отправляем на сервер
                }
            } else if (isEnabled) {
                // При включенном модуле, но без полета, применяем гравитацию
                val motionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = session.localPlayer.runtimeEntityId
                    motion = Vector3f.from(0f, -0.08f, 0f) // Только гравитация
                }
                session.clientBound(motionPacket)
                lastMotion = Vector3f.ZERO

                // Синхронизируем позицию
                val playerPosition = packet.position?.let { Vector3f.from(it.getX(), it.getY(), it.getZ()) } ?: Vector3f.ZERO
                if (playerPosition != Vector3f.ZERO) {
                    val movePacket = MovePlayerPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        position = playerPosition
                        rotation = packet.rotation?.let { Vector3f.from(it.getX(), it.getY(), it.getZ()) } ?: Vector3f.ZERO
                        mode = MovePlayerPacket.Mode.NORMAL
                        setOnGround(false) // Игрок падает
                        tick = packet.tick
                    }
                    session.serverBound(movePacket)
                }
            }
        }
    }
}
