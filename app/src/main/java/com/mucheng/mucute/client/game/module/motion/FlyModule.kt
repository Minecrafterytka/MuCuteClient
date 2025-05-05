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
import java.util.EnumSet

class FlyModule : Module("fly", ModuleCategory.Motion) {
    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    // Сохраняем полные права и способности для совместимости с мини-играми
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

        // 1. Блокируем запросы на активацию полета
        if (packet is RequestAbilityPacket && packet.ability == Ability.FLYING) {
            interceptablePacket.intercept()
            return
        }

        // 2. Блокируем обновление способностей от клиента
        if (packet is UpdateAbilitiesPacket) {
            interceptablePacket.intercept()
            return
        }

        // 3. Обработка ввода с сохранением всех возможностей
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

            // 4. Чистим флаги полета из PlayerAuthInputPacket
            val modifiedInputData = EnumSet.copyOf(packet.inputData).apply {
                remove(PlayerAuthInputData.START_FLYING)
                remove(PlayerAuthInputData.STOP_FLYING)
            }

            // Клонируем пакет с чистыми данными
            val modifiedPacket = packet.clone().apply {
                inputData.clear()
                inputData.addAll(modifiedInputData)
            }

            interceptablePacket.packet = modifiedPacket

            // 5. Вертикальное движение без влияния на горизонтальное
            if (isEnabled) {
                // Сохраняем текущее движение
                var currentMotionX = packet.motion.x
                var currentMotionZ = packet.motion.z

                // Вычисляем вертикальную компоненту
                var verticalMotion = 0f
                if (modifiedInputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMotion = flySpeed
                } else if (modifiedInputData.contains(PlayerAuthInputData.SNEAKING)) {
                    verticalMotion = -flySpeed
                }

                // Отправляем пакет с сохранением горизонтального движения
                if (verticalMotion != 0f) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = Vector3f.from(currentMotionX, verticalMotion, currentMotionZ)
                    }
                    session.clientBound(motionPacket)
                }
            }
        }

        // 6. Скрываем флаг CAN_FLY в метаданных (на всякий случай)
        if (packet is SetEntityDataPacket) {
            val flags = packet.metadata.get(EntityDataTypes.FLAGS) ?: return
            val modifiedFlags = EnumSet.copyOf(flags).apply {
                remove(EntityFlag.CAN_FLY)
            }
            packet.metadata.put(EntityDataTypes.FLAGS, modifiedFlags)
        }
    }
}
