package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission
import org.cloudburstmc.protocol.bedrock.packet.*
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
                    Ability.FLY_SPEED,
                    Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
        })
    }
    
    private var canFly = false

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet
        
        if (packet is RequestAbilityPacket && packet.ability == Ability.FLYING) {
            interceptablePacket.intercept()
            return
        }
        
        if (packet is UpdateAbilitiesPacket) {
            interceptablePacket.intercept()
            return
        }
        
        if (packet is PlayerAuthInputPacket) {
            // Создаем копию inputData без флагов START_FLYING/STOP_FLYING
            val filteredInputData = EnumSet.copyOf(packet.inputData).apply {
                remove(PlayerAuthInputData.START_FLYING)
                remove(PlayerAuthInputData.STOP_FLYING)
            }

            // Заменяем inputData в пакете (создаем новый пакет)
            val modifiedPacket = packet.clone().apply {
                this.inputData.clear()
                this.inputData.addAll(filteredInputData)
            }

            // Заменяем исходный пакет
            interceptablePacket.packet = modifiedPacket

            // Enable/disable flying abilities
            if (!canFly && isEnabled) {
                enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(enableFlyAbilitiesPacket)
                canFly = true
            } else if (canFly && !isEnabled) {
                disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(disableFlyAbilitiesPacket)
                canFly = false
                return
            }
            
            // Handle vertical movement when enabled
            if (isEnabled) {
                var verticalMotion = 0f
                // Space for up, Shift for down
                if (filteredInputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMotion = flySpeed
                } else if (filteredInputData.contains(PlayerAuthInputData.SNEAKING)) {
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
