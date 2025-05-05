package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag
import java.util.EnumSet

class FlyModule : Module("fly", ModuleCategory.Motion) {
    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                listOf(
                    Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS, Ability.MAY_FLY, Ability.FLY_SPEED, Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
            flySpeed = this@FlyModule.flySpeed
        })
    }

    private val disableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                listOf(
                    Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS, Ability.FLY_SPEED, Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
        })
    }

    private var isClientFlyAbilitySet = false

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Блокируем входящие пакеты способностей от сервера
        if ((packet is RequestAbilityPacket && packet.ability == Ability.FLYING) || packet is UpdateAbilitiesPacket) {
             if (isEnabled && isClientFlyAbilitySet) {
                 interceptablePacket.intercept()
                 return
             }
             return
        }

        // Обработка исходящего пакета ввода игрока (PlayerAuthInputPacket)
        if (packet is PlayerAuthInputPacket) {
            // Переключаем способности полета у клиента при включении/выключении модуля
            if (!isClientFlyAbilitySet && isEnabled) {
                enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(enableFlyAbilitiesPacket)
                isClientFlyAbilitySet = true
                log.debug("Fly module enabled. Sent enable abilities packet to client.")
            } else if (isClientFlyAbilitySet && !isEnabled) {
                disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(disableFlyAbilitiesPacket)
                isClientFlyAbilitySet = false
                log.debug("Fly module disabled. Sent disable abilities packet to client.")
            }

            // Обработка, только если модуль включен
            if (isEnabled) {
                // Эмулируем вертикальное движение для клиента
                // FIX: Доступ к компонентам Vector3f осуществляется через свойства (.x, .z)
                val currentMotionX = packet.motion.x
                val currentMotionZ = packet.motion.z

                var verticalMotion = 0f
                if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMotion = flySpeed
                } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                    verticalMotion = -flySpeed
                }

                if (verticalMotion != 0f) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = Vector3f.from(currentMotionX, verticalMotion, currentMotionZ)
                    }
                    session.clientBound(motionPacket)
                }

                // Чистим флаги полета из PlayerAuthInputPacket, идущего к серверу
                 val originalInputData = packet.inputData

                 val modifiedInputData = EnumSet.noneOf(PlayerAuthInputData::class.java).apply {
                     addAll(originalInputData)
                     remove(PlayerAuthInputData.START_FLYING)
                     remove(PlayerAuthInputData.STOP_FLYING)
                 }

                if (modifiedInputData != originalInputData) {
                    originalInputData.clear()
                    originalInputData.addAll(modifiedInputData)
                 }
            }
        }

        // Обработка исходящего пакета MovePlayerPacket (обход античита onGround)
        if (packet is MovePlayerPacket) {
             if (isEnabled) {
                 // Всегда сообщаем серверу, что мы НА ЗЕМЛЕ
                 packet.onGround = true
             }
        }

        // Скрываем флаг CAN_FLY в метаданных существа (входящий от сервера)
        if (packet is SetEntityDataPacket) {
            val flags = packet.metadata.get(EntityDataTypes.FLAGS)
            if (flags != null) {
                 // FIX: Используем apply для создания и мутации копии
                 val modifiedFlags = EnumSet.copyOf(flags).apply {
                     remove(EntityFlag.CAN_FLY)
                 }
                 packet.metadata.put(EntityDataTypes.FLAGS, modifiedFlags)
            }
        }
    }

    // Метод для обновления скорости полета из UI
    override fun onValueChange(valueName: String, oldValue: Any, newValue: Any) {
        super.onValueChange(valueName, oldValue, newValue)
        if (valueName == "flySpeed") {
            enableFlyAbilitiesPacket.abilityLayers.firstOrNull()?.flySpeed = flySpeed
            if (isEnabled && isClientFlyAbilitySet) {
                 enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                 session.clientBound(enableFlyAbilitiesPacket)
                 log.debug("Fly speed setting updated. Sent updated abilities packet to client.")
            }
        }
    }
}
