// Package declaration
package com.mucheng.mucute.client.game.module.motion

// MuCuteClient Imports
import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory

// CloudburstMC Protocol Bedrock Imports (Packet Types)
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket // Required for SetEntityDataPacket handling

// CloudburstMC Protocol Bedrock Data Imports
import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission

// CloudburstMC Protocol Bedrock Entity Data Imports
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes // Required for EntityDataTypes.FLAGS
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag // Required for EntityFlag.CAN_FLY

// CloudburstMC Math Imports
import org.cloudburstmc.math.vector.Vector3f // Required for Vector3f

// Java Utility Imports
import java.util.EnumSet // Required for EnumSet

// --- Class Definition ---

class FlyModule : Module("fly", ModuleCategory.Motion) {
    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    // Полный набор способностей для мини-игр
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

        // Блокируем запросы на активацию полета
        if (packet is RequestAbilityPacket && packet.ability == Ability.FLYING) {
            interceptablePacket.intercept()
            return
        }

        // Блокируем обновление способностей от клиента
        if (packet is UpdateAbilitiesPacket) {
            interceptablePacket.intercept()
            return
        }

        // Обработка ввода
        if (packet is PlayerAuthInputPacket) {
            // Переключаем способности полета
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

            // Вертикальное движение без изменения флагов
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
                        // Эта строка обнуляет горизонтальное движение
                        motion = Vector3f.from(0f, verticalMotion, 0f)
                    }
                    session.clientBound(motionPacket)
                }
            }

            // Чистим флаги полета из PlayerAuthInputPacket
            val modifiedInputData = EnumSet.copyOf(packet.inputData).apply {
                remove(PlayerAuthInputData.START_FLYING)
                remove(PlayerAuthInputData.STOP_FLYING)
            }

            val modifiedPacket = packet.clone().apply {
                inputData.clear()
                inputData.addAll(modifiedInputData)
            }

            interceptablePacket.packet = modifiedPacket
        }

        // Скрываем флаг CAN_FLY в метаданных
        if (packet is SetEntityDataPacket) {
            val flags = packet.metadata.get(EntityDataTypes.FLAGS) ?: return
            // FIX: Используем apply для создания и мутации копии
            val modifiedFlags = EnumSet.copyOf(flags).apply {
                remove(EntityFlag.CAN_FLY)
            }
            packet.metadata.put(EntityDataTypes.FLAGS, modifiedFlags)
        }
    }
}
