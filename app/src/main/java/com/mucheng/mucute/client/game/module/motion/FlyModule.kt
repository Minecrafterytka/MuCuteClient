package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag
import org.cloudburstmc.protocol.common.PacketSignal

import java.util.EnumSet
import kotlin.math.cos
import kotlin.math.sin

class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.1f, 0.05f..0.3f)
    private var isFlyingLocally = false // Отслеживаем локальный статус полета

    // Пакет для включения способностей полета на клиенте
    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.MEMBER
        commandPermission = CommandPermission.NORMAL
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.DEFAULT_ABILITIES)
            abilitiesSet.add(Ability.MAY_FLY)
            abilitiesSet.add(Ability.FLYING)
            abilityValues.addAll(Ability.DEFAULT_ABILITIES)
            abilityValues.add(Ability.MAY_FLY)
            abilityValues.add(Ability.FLYING)
            abilityValues.add(Ability.FLY_SPEED)
            walkSpeed = 0.1f
            // flySpeed будет установлен перед отправкой
        })
    }

    // Пакет для выключения способностей полета на клиенте
    private val disableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.MEMBER
        commandPermission = CommandPermission.NORMAL
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.DEFAULT_ABILITIES)
            abilityValues.addAll(Ability.DEFAULT_ABILITIES)
            walkSpeed = 0.1f
            flySpeed = 0.05f // Стандартная скорость
        })
    }

    // Перехват ВХОДЯЩИХ пакетов
    override fun beforePacketBound(interceptablePacket: InterceptablePacket): PacketSignal {
        if (!interceptablePacket.isClientBound) return PacketSignal.CONTINUE
        val packet = interceptablePacket.packet

        // Блокируем UpdateAbilitiesPacket от сервера, если модуль включен
        if (packet is UpdateAbilitiesPacket && isEnabled) {
            // logger.debug("Blocked incoming UpdateAbilitiesPacket") // Комментарий для лога убран
            sendEnablePacketIfNeeded()
            return PacketSignal.SUPPRESS
        }
        return PacketSignal.CONTINUE
    }

    // Перехват ИСХОДЯЩИХ пакетов
    override fun beforePacketServer(interceptablePacket: InterceptablePacket): PacketSignal {
        if (!interceptablePacket.isServerBound) return PacketSignal.CONTINUE
        val packet = interceptablePacket.packet

        // Если модуль выключен, отключаем фейковые способности и пропускаем пакет
        if (!isEnabled) {
            sendDisablePacketIfNeeded()
            return PacketSignal.CONTINUE
        }

        // --- Логика включенного модуля ---

        // Блокируем RequestAbilityPacket(FLYING)
        if (packet is RequestAbilityPacket && packet.ability == Ability.FLYING) {
            // logger.debug("Blocked outgoing RequestAbilityPacket(FLYING)") // Комментарий для лога убран
            return PacketSignal.SUPPRESS
        }

        // Модифицируем SetEntityDataPacket: Убираем флаги полета и глайдинга
        if (packet is SetEntityDataPacket) {
            if (session.localPlayer != null && packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
                var modified = false
                // Обрабатываем FLAGS
                packet.metadata.get(EntityDataTypes.FLAGS)?.let { flags ->
                    if (flags.contains(EntityFlag.CAN_FLY) || flags.contains(EntityFlag.GLIDING) || flags.contains(EntityFlag.WASD_AIR_CONTROLLED)) {
                        val mutableFlags = EnumSet.copyOf(flags)
                        mutableFlags.remove(EntityFlag.CAN_FLY)
                        mutableFlags.remove(EntityFlag.GLIDING) // Убираем и глайдинг
                        mutableFlags.remove(EntityFlag.WASD_AIR_CONTROLLED)
                        packet.metadata.put(EntityDataTypes.FLAGS, mutableFlags)
                        modified = true
                    }
                }
                // Обрабатываем FLAGS_2
                packet.metadata.get(EntityDataTypes.FLAGS_2)?.let { flags2 ->
                     if (flags2.contains(EntityFlag.CAN_FLY) || flags2.contains(EntityFlag.GLIDING) || flags2.contains(EntityFlag.WASD_AIR_CONTROLLED)) {
                        val mutableFlags2 = EnumSet.copyOf(flags2)
                        mutableFlags2.remove(EntityFlag.CAN_FLY)
                        mutableFlags2.remove(EntityFlag.GLIDING) // Убираем и глайдинг
                        mutableFlags2.remove(EntityFlag.WASD_AIR_CONTROLLED)
                        packet.metadata.put(EntityDataTypes.FLAGS_2, mutableFlags2)
                        modified = true
                    }
                }
                // if (modified) logger.trace("Removed flying/gliding flags from outgoing SetEntityDataPacket") // Комментарий для лога убран
            }
        }

        // Обрабатываем PlayerAuthInputPacket: Симулируем движение локально, убираем сигналы серверу
        if (packet is PlayerAuthInputPacket) {
            sendEnablePacketIfNeeded() // Убедимся, что клиент "думает", что может летать

            // --- Расчет и симуляция движения ЛОКАЛЬНО ---
            var verticalMotion = 0f
            if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                verticalMotion = flySpeed
            } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                verticalMotion = -flySpeed
            }

            val inputVec: Vector2f = packet.analogMoveVector ?: packet.rawMoveVector ?: Vector2f.ZERO
            val yawRad = Math.toRadians(packet.rotation.y.toDouble()).toFloat()
            val motionX = (inputVec.x * cos(yawRad) - inputVec.y * sin(yawRad)) * flySpeed
            val motionZ = (inputVec.x * sin(yawRad) + inputVec.y * cos(yawRad)) * flySpeed

            val simulatedVelocity = Vector3f.from(motionX, verticalMotion, motionZ)

            // Отправляем SetEntityMotionPacket КЛИЕНТУ для локального движения
            if (simulatedVelocity.lengthSquared() > 1e-9f && session.isReady) {
                val motionPacket = SetEntityMotionPacket().apply {
                    runtimeEntityId = session.localPlayer.runtimeEntityId
                    motion = simulatedVelocity
                }
                session.clientBound(motionPacket)
            }

            // --- Модификация ИСХОДЯЩЕГО пакета (только сигналы) ---
            packet.inputData.remove(PlayerAuthInputData.START_FLYING)
            packet.inputData.remove(PlayerAuthInputData.STOP_FLYING)
            // if (flagsModified) logger.trace("Removed START/STOP_FLYING flags") // Комментарий для лога убран

            // НЕ МЕНЯЕМ position/delta, НЕ УДАЛЯЕМ JUMPING/SNEAKING
        }

        // Модифицируем MovePlayerPacket: Устанавливаем onGround = false
        if (packet is MovePlayerPacket) {
            if (packet.isOnGround) {
                 packet.onGround = false
                 // logger.trace("Set onGround=false in outgoing MovePlayerPacket") // Комментарий для лога убран
            }
            // НЕ МЕНЯЕМ position
        }

        return PacketSignal.CONTINUE // Пропускаем пакет на сервер
    }

    // --- Вспомогательные функции и управление состоянием ---

    private fun sendEnablePacketIfNeeded() {
        if (!isFlyingLocally && isEnabled && session.isReady) {
            enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.runtimeEntityId
            enableFlyAbilitiesPacket.abilityLayers[0].flySpeed = this.flySpeed
            session.clientBound(enableFlyAbilitiesPacket)
            isFlyingLocally = true
            // logger.debug("Sent fake 'Enable Fly' abilities to client") // Комментарий для лога убран
        }
    }

    private fun sendDisablePacketIfNeeded() {
         if (isFlyingLocally && session.isReady) {
             disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.runtimeEntityId
             session.clientBound(disableFlyAbilitiesPacket)
             isFlyingLocally = false
             // logger.debug("Sent fake 'Disable Fly' abilities to client") // Комментарий для лога убран
         }
    }

    override fun onEnable() {
        super.onEnable()
        // logger.info("Fly module enabled") // Комментарий для лога убран
    }

    override fun onDisable() {
        super.onDisable()
        sendDisablePacketIfNeeded()
        // logger.info("Fly module disabled") // Комментарий для лога убран
    }

    override fun onDisconnect() {
        super.onDisconnect()
        isFlyingLocally = false
    }
}
