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
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket

class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    // Пакеты для включения/выключения полета на стороне клиента (чтобы работало управление)
    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                arrayOf(
                    Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES, Ability.OPEN_CONTAINERS,
                    Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS, Ability.OPERATOR_COMMANDS,
                    Ability.MAY_FLY, // Включаем полет для клиента
                    Ability.FLY_SPEED, Ability.WALK_SPEED
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
                     Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES, Ability.OPEN_CONTAINERS,
                     Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS, Ability.OPERATOR_COMMANDS,
                     // Ability.MAY_FLY не добавляем
                     Ability.FLY_SPEED, Ability.WALK_SPEED
                 )
             )
            walkSpeed = 0.1f
         })
    }

    // Флаг для отслеживания, отправили ли мы клиенту пакет, разрешающий полет
    private var clientFlightEnabled = false

    override fun onEnable() {
        if (session?.localPlayer != null) {
             enableClientFlight()
        }
    }

    override fun onDisable() {
        if (session?.localPlayer != null) {
            disableClientFlight()
        }
    }

    private fun enableClientFlight() {
        if (!clientFlightEnabled && session?.localPlayer != null) {
            val packetToSend = enableFlyAbilitiesPacket.clone()
            packetToSend.uniqueEntityId = session.localPlayer.uniqueEntityId
            packetToSend.abilityLayers.find { it.layerType == AbilityLayer.Type.BASE }?.flySpeed = this.flySpeed
            session.clientBound(packetToSend) // Отправляем пакет клиенту
            clientFlightEnabled = true
        }
    }

     private fun disableClientFlight() {
         if (clientFlightEnabled && session?.localPlayer != null) {
             val packetToSend = disableFlyAbilitiesPacket.clone()
             packetToSend.uniqueEntityId = session.localPlayer.uniqueEntityId
             session.clientBound(packetToSend) // Отправляем пакет клиенту
             clientFlightEnabled = false
         }
     }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Перехватываем пакеты ОТ сервера, связанные со способностями или движением, если модуль включен
        if (interceptablePacket.fromServer && isEnabled && session?.localPlayer != null) {
            when (packet) {
                is RequestAbilityPacket -> {
                    // Сервер запрашивает состояние способности FLYING. Перехватываем.
                     if (packet.ability == Ability.FLYING) {
                         interceptablePacket.intercept()
                         return
                     }
                }
                is UpdateAbilitiesPacket -> {
                    // Сервер пытается обновить способности. Перехватываем, чтобы он не отключил MAY_FLY.
                    interceptablePacket.intercept()
                    return
                }
                is MovePlayerPacket -> {
                    // Сервер корректирует позицию или телепортирует нашего игрока. Перехватываем.
                    if (packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
                        interceptablePacket.intercept()
                        return
                    }
                }
                is SetEntityMotionPacket -> {
                    // Сервер устанавливает движение (скорость) нашего игрока. Перехватываем.
                     if (packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
                         interceptablePacket.intercept()
                         return
                     }
                }
            }
        }

        // Перехватываем пакеты ОТ клиента, связанные с движением, если модуль включен
        if (interceptablePacket.fromClient && isEnabled) {
            if (packet is PlayerAuthInputPacket) {
                val inputData = packet.inputData

                // Удаляем флаги, явно выдающие полет или его стандартные контроли
                inputData.remove(PlayerAuthInputData.START_FLYING)
                inputData.remove(PlayerAuthInputData.STOP_FLYING)
                inputData.remove(PlayerAuthInputData.ASCEND)
                inputData.remove(PlayerAuthInputData.DESCEND)
                inputData.remove(PlayerAuthInputData.CHANGE_HEIGHT)
                inputData.remove(PlayerAuthInputData.JUMPING) // Удаляем, т.к. используется для подъема в полете
                inputData.remove(PlayerAuthInputData.SNEAKING) // Удаляем, т.к. используется для спуска в полете

                // packet.position и packet.delta отправляются без изменений, отражая реальное положение и движение клиента
            }
        }
    }
}
