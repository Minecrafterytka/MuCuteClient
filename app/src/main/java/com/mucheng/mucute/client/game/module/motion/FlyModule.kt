package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData

class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)
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
                // Создаем мутабельную копию inputData
                val modifiedInputData = packet.inputData.toMutableSet()
                // Удаляем флаги полета
                modifiedInputData.remove(PlayerAuthInputData.START_FLYING)
                modifiedInputData.remove(PlayerAuthInputData.STOP_FLYING)

                // Создаем новый пакет
                val newPacket = PlayerAuthInputPacket()
                // Копируем данные из оригинального пакета
                newPacket.rotation = packet.rotation?.let { Vector3f.from(it.x, it.y, it.z) } ?: Vector3f.ZERO
                newPacket.position = packet.position?.let { Vector3f.from(it.x, it.y, it.z) } ?: Vector3f.ZERO
                newPacket.motion = packet.motion?.let { Vector2f.from(it.x, it.y) } ?: Vector2f.ZERO
                newPacket.inputData.addAll(modifiedInputData) // Добавляем измененные данные
                newPacket.inputMode = packet.inputMode
                newPacket.playMode = packet.playMode
                newPacket.tick = packet.tick
                newPacket.delta = packet.delta?.let { Vector3f.from(it.x, it.y, it.z) } ?: Vector3f.ZERO
                // Дополнительные поля
                newPacket.vrGazeDirection = packet.vrGazeDirection
                newPacket.itemUseTransaction = packet.itemUseTransaction
                newPacket.itemStackRequest = packet.itemStackRequest
                newPacket.playerActions.addAll(packet.playerActions)
                newPacket.inputInteractionModel = packet.inputInteractionModel
                newPacket.interactRotation = packet.interactRotation
                newPacket.analogMoveVector = packet.analogMoveVector
                newPacket.predictedVehicle = packet.predictedVehicle
                newPacket.vehicleRotation = packet.vehicleRotation
                newPacket.cameraOrientation = packet.cameraOrientation
                newPacket.rawMoveVector = packet.rawMoveVector

                // Заменяем старый пакет новым
                interceptablePacket.packet = newPacket

                // Управляем движением локально
                var verticalMotion = 0f
                if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMotion = flySpeed
                } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                    verticalMotion = -flySpeed
                }

                if (verticalMotion != 0f) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = Vector3f.from(0f, verticalMotion, 0f)
                    }
                    session.clientBound(motionPacket)
                }
            }
        }
    }
}
