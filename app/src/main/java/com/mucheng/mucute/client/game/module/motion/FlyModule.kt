package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory

import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket // Убедимся, что этот импорт есть

import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes // Убедимся, что этот импорт есть
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag // Убедимся, что этот импорт есть

import org.cloudburstmc.math.vector.Vector3f // Убедимся, что этот импорт есть

import java.util.EnumSet // Убедимся, что этот импорт есть

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
                    Ability.OPERATOR_COMMANDS,
                    Ability.FLY_SPEED, Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
        })
    }

    private var canFly = false

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet
        if ((packet is RequestAbilityPacket && packet.ability == Ability.FLYING) || packet is UpdateAbilitiesPacket) {
             if (isEnabled && canFly) {
                 interceptablePacket.intercept()
                 return
             }
             return
        }

        if (packet is PlayerAuthInputPacket) {
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

                // Скрываем флаги полета из PlayerAuthInputPacket перед отправкой на сервер
                // Используем обход ошибки 'val cannot be reassigned'
                val originalInputData = packet.inputData
                val modifiedInputData = EnumSet.noneOf(PlayerAuthInputData::class.java)
                for (data in originalInputData) {
                    if (data != PlayerAuthInputData.START_FLYING && data != PlayerAuthInputData.STOP_FLYING) {
                        modifiedInputData.add(data)
                    }
                }

                 if (modifiedInputData != originalInputData) {
                     val modifiedPacket = packet.clone().apply {
                         inputData.clear()
                         inputData.addAll(modifiedInputData)
                     }
                     interceptablePacket.packet = modifiedPacket
                 }
            }
        }

        // Блок SetEntityDataPacket с использованием кода, который вы предоставили
        if (packet is SetEntityDataPacket) {
            val metadata = packet.metadata
            if (metadata.containsKey(EntityDataTypes.FLAGS)) {
                // Этот код использует EnumSet.copyOf, который ранее вызывал ошибку компиляции
                val flags = metadata.get(EntityDataTypes.FLAGS) as? EnumSet<EntityFlag> ?: EnumSet.noneOf(EntityFlag::class.java)

                val modifiedFlags = EnumSet.copyOf(flags) // Ранее ошибка была здесь

                modifiedFlags.remove(EntityFlag.CAN_FLY)

                metadata.put(EntityDataTypes.FLAGS, modifiedFlags)
            }
        }
    }
}
