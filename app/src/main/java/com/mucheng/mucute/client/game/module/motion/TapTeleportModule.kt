package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission
import org.cloudburstmc.protocol.bedrock.packet.EntityFallPacket
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import kotlin.random.Random

class TapTeleportModule : Module("tap_teleport", ModuleCategory.Motion) {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val enableNoClipAbilitiesPacket = UpdateAbilitiesPacket().apply {
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
                    Ability.MAY_FLY,
                    Ability.FLY_SPEED,
                    Ability.WALK_SPEED,
                    Ability.OPERATOR_COMMANDS
                )
            )
            abilityValues.add(Ability.NO_CLIP)
            walkSpeed = 0.1f
            flySpeed = 0.15f
        })
    }

    private val disableNoClipAbilitiesPacket = UpdateAbilitiesPacket().apply {
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
                    Ability.FLY_SPEED,
                    Ability.WALK_SPEED,
                    Ability.OPERATOR_COMMANDS
                )
            )
            abilityValues.remove(Ability.NO_CLIP)
            walkSpeed = 0.1f
        })
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled) return

        val packet = interceptablePacket.packet

        if (packet is InventoryTransactionPacket) {
            if (packet.transactionType != org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType.ITEM_USE) {
                return
            }

            interceptablePacket.intercept() // Intercept the packet to prevent default server handling

            val pos = packet.blockPosition
            val face = packet.blockFace

            var x = pos.x.toFloat() + 0.5f + Random.nextFloat() * 0.1f - 0.05f
            var y = pos.y.toFloat() + Random.nextFloat() * 0.1f - 0.05f
            var z = pos.z.toFloat() + 0.5f + Random.nextFloat() * 0.1f - 0.05f

            when (face) {
                0 -> y += 1f
                1 -> y += 1f
                2 -> z -= 1f
                3 -> z += 1f
                4 -> x -= 1f
                5 -> x += 1f
            }

            val targetPos = Vector3f.from(x, y + 2f, z)

            coroutineScope.launch {
                teleportTo(targetPos)
                sendFallDamageReset()
            }
        }
    }

    private fun teleportTo(position: Vector3f) {
        val movePlayerPacket = MovePlayerPacket().apply {
            runtimeEntityId = session.localPlayer.runtimeEntityId
            this.position = position
            // Use session.localPlayer.rotation directly from the Player class
            this.rotation = session.localPlayer.rotation ?: Vector3f.ZERO
            mode = MovePlayerPacket.Mode.NORMAL
            onGround = true
            ridingRuntimeEntityId = 0
            // Use session.localPlayer.tickExists as confirmed by LocalPlayer class
            this.tick = session.localPlayer.tickExists
        }
        session.clientBound(movePlayerPacket)
    }

    private fun sendFallDamageReset() {
        val fallPacket = EntityFallPacket().apply {
            runtimeEntityId = session.localPlayer.runtimeEntityId
            fallDistance = 0f
            inVoid = false
        }
        session.clientBound(fallPacket)
    }
}
