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
import kotlin.math.toRadians

class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.2f, 0.05f..1.0f) // Уменьшена базовая скорость
    private var canFly = false

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
            // Включаем полет локально
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
                // Перехватываем пакеты с флагами полета
                if (packet.inputData.contains(PlayerAuthInputData.START_FLYING) ||
                    packet.inputData.contains(PlayerAuthInputData.STOP_FLYING)) {
                    interceptablePacket.intercept() // Отменяем отправку пакета
                    return
                }

                // Управляем движением локально
                var verticalMotion = 0f
                if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMotion = flySpeed * 1.2f // Уменьшен множитель для вертикального движения
                } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                    verticalMotion = -flySpeed * 1.2f // Уменьшен множитель для спуска
                }

                // Получаем горизонтальное движение из packet.motion
                val inputMotion = packet.motion?.let {
                    Vector3f.from(it.x, 0f, it.y) // y в motion соответствует z в 3D
                } ?: Vector3f.ZERO

                // Получаем угол поворота (yaw) из rotation
                val yaw = packet.rotation?.y?.toRadians() ?: 0.0
                // Преобразуем движение в направлении взгляда
                val horizontalMotion = if (inputMotion != Vector3f.ZERO) {
                    val speed = flySpeed * 1.0f // Уменьшен множитель для горизонтального движения
                    // Вращаем вектор движения в соответствии с yaw
                    Vector3f.from(
                        (-sin(yaw) * inputMotion.z + cos(yaw) * inputMotion.x) * speed,
                        0f,
                        (cos(yaw) * inputMotion.z + sin(yaw) * inputMotion.x) * speed
                    )
                } else {
                    Vector3f.ZERO
                }

                // Комбинируем горизонтальное и вертикальное движение
                val combinedMotion = Vector3f.from(
                    horizontalMotion.x,
                    verticalMotion,
                    horizontalMotion.z
                )

                // Отправляем SetEntityMotionPacket только если есть движение
                if (combinedMotion != Vector3f.ZERO) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = combinedMotion
                    }
                    session.clientBound(motionPacket)
                }

                // Синхронизируем позицию с сервером через MovePlayerPacket
                val playerPosition = packet.position?.let { Vector3f.from(it.x, it.y, it.z) } ?: Vector3f.ZERO
                if (playerPosition != Vector3f.ZERO) {
                    val movePacket = MovePlayerPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        position = playerPosition
                        rotation = packet.rotation?.let { Vector3f.from(it.x, it.y, it.z) } ?: Vector3f.ZERO
                        mode = MovePlayerPacket.Mode.NORMAL
                        setOnGround(false) // Игрок в полете
                        tick = packet.tick
                    }
                    session.serverBound(movePacket) // Отправляем на сервер
                }
            }
        }
    }
}
