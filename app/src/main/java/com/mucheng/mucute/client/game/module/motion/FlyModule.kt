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

    private var flySpeed by floatValue("flySpeed", 0.3f, 0.05f..1.0f)
    private var verticalSpeed by floatValue("verticalSpeed", 0.3f, 0.1f..1.0f)
    private var canFly = false

    private fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)

    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                listOf(
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
                listOf(
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

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        if (packet is RequestAbilityPacket && packet.ability == Ability.FLYING) {
            interceptablePacket.intercept()
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
                if (packet.inputData.contains(PlayerAuthInputData.START_FLYING) ||
                    packet.inputData.contains(PlayerAuthInputData.STOP_FLYING)) {
                    interceptablePacket.intercept()
                }

                val isFlying = packet.inputData.contains(PlayerAuthInputData.JUMPING) ||
                        packet.inputData.contains(PlayerAuthInputData.SNEAKING)

                var verticalMotion = 0f
                if (isFlying) {
                    if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                        verticalMotion = verticalSpeed
                    } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                        verticalMotion = -verticalSpeed
                    }
                }

                val inputMotion = packet.motion?.let {
                    Vector3f.from(it.x, 0f, it.y)
                } ?: Vector3f.ZERO

                val yaw = packet.rotation?.y?.toDouble()?.let { toRadians(it) } ?: 0.0
                val horizontalMotion = if (inputMotion != Vector3f.ZERO && isFlying) {
                    val speed = flySpeed.toDouble()
                    Vector3f.from(
                        ((-sin(yaw) * inputMotion.z.toDouble() + cos(yaw) * inputMotion.x.toDouble()) * speed).toFloat(),
                        0f,
                        ((cos(yaw) * inputMotion.z.toDouble() + sin(yaw) * inputMotion.x.toDouble()) * speed).toFloat()
                    )
                } else {
                    Vector3f.ZERO
                }

                val combinedMotion = Vector3f.from(
                    horizontalMotion.x,
                    verticalMotion,
                    horizontalMotion.z
                )

                if (isFlying && combinedMotion != Vector3f.ZERO) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = combinedMotion
                    }
                    session.clientBound(motionPacket)
                } else if (isFlying) {
                    val stopMotionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = Vector3f.ZERO
                    }
                    session.clientBound(stopMotionPacket)
                }

                val playerPosition = packet.position?.let { Vector3f.from(it.x, it.y, it.z) } ?: Vector3f.ZERO
                if (playerPosition != Vector3f.ZERO) {
                    val movePacket = MovePlayerPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        position = playerPosition
                        rotation = packet.rotation?.let { Vector3f.from(it.x, it.y, it.z) } ?: Vector3f.ZERO
                        mode = MovePlayerPacket.Mode.NORMAL
                        tick = packet.tick
                    }
                    session.serverBound(movePacket)
                }
            }
        }
    }
}
