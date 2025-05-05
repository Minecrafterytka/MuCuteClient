package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory

import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket // Все еще нужен для эмуляции вертикального движения
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket // Все еще нужен для SetEntityDataPacket handling

import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData // Убедимся, что этот импорт есть
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission

import org.cloudburstmc.math.vector.Vector3f // Убедимся, что этот импорт есть

import java.util.EnumSet // Нужно добавить для работы с EnumSet
import java.util.Collection // Может понадобиться для работы с коллекциями, хотя EnumSet достаточно
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes // Необходим для EntityDataTypes.FLAGS
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag // Необходим для EntityFlag.CAN_FLY


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
    // private var tickCounter = 0 // Этот счетчик пока нигде не используется для логики движения

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet
        if ((packet is RequestAbilityPacket && packet.ability == Ability.FLYING) || packet is UpdateAbilitiesPacket) {
             if (isEnabled && canFly) { // Блокируем только если модуль включен и полет активирован нами
                 interceptablePacket.intercept()
                 return
             }
             return // Пропускаем, если не наш пакет или модуль выключен
        }

        if (packet is PlayerAuthInputPacket) {
            // Enable/disable flying abilities (отправляем клиенту)
            if (!canFly && isEnabled) {
                enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(enableFlyAbilitiesPacket)
                canFly = true
                // log.debug("Fly module enabled. Sent enable abilities packet to client.") // Убран лог
            } else if (canFly && !isEnabled) {
                disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(disableFlyAbilitiesPacket)
                canFly = false
                 // Убираем 'return' из исходного кода, чтобы пакет прошел дальше после выключения модуля
            }

            // Обработка, только если модуль включен
            if (isEnabled) {
                // **ИСПРАВЛЕНО:** Скрываем флаги полета из PlayerAuthInputPacket перед отправкой на сервер
                // Используем обход ошибки 'val cannot be reassigned' с ручным созданием EnumSet
                val originalInputData = packet.inputData // Получаем исходный набор флагов

                // Создаем НОВЫЙ, пустой EnumSet правильного типа вручную
                val modifiedInputData = EnumSet.noneOf(PlayerAuthInputData::class.java)
                // Копируем все флаги из оригинального набора, кроме тех, что хотим удалить
                for (data in originalInputData) {
                    if (data != PlayerAuthInputData.START_FLYING && data != PlayerAuthInputData.STOP_FLYING) {
                        modifiedInputData.add(data)
                    }
                }

                // Создаем клон пакета с модифицированными данными и заменяем его
                // Проверяем, были ли изменения, чтобы не клонировать пакет без необходимости (опционально)
                // Проверка modifiedInputData != originalInputData для EnumSet может работать не всегда,
                // но если мы всегда клонируем и заменяем пакет, это безопаснее.
                // Восстановим логику клонирования из более ранних версий, которая работала
                 val modifiedPacket = packet.clone().apply {
                     inputData.clear() // Очищаем оригинальный набор в клоне
                     inputData.addAll(modifiedInputData) // Добавляем модифицированный набор
                     // Остальные данные пакета (позиция, движение, вращение) остаются как в оригинале
                 }
                 interceptablePacket.packet = modifiedPacket // Заменяем пакет


                // Handle vertical movement when enabled (как в вашей основе)
                var verticalMotion = 0f
                // Space for up, Shift for down
                if (originalInputData.contains(PlayerAuthInputData.JUMPING)) { // Используем originalInputData для проверки ввода
                    verticalMotion = flySpeed
                } else if (originalInputData.contains(PlayerAuthInputData.SNEAKING)) { // Используем originalInputData для проверки ввода
                    verticalMotion = -flySpeed
                }

                if (verticalMotion != 0f) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        // Как в вашей основе: Эта строка обнуляет горизонтальное движение
                        motion = Vector3f.from(0f, verticalMotion, 0f)
                    }
                    session.clientBound(motionPacket) // Отправляем ТОЛЬКО клиенту
                }

                 // **УДАЛЕННЫЙ КОД:** tickCounter++
                 // tickCounter пока не используется для логики движения
            }
            // Пакет PlayerAuthInputPacket (теперь с очищенными флагами полета, если модуль включен) отправляется дальше к серверу.
        }

        // **ИСПРАВЛЕНО:** Скрываем флаг CAN_FLY в метаданных (входящий от сервера)
        if (packet is SetEntityDataPacket) {
            val metadata = packet.metadata
            if (metadata.containsKey(EntityDataTypes.FLAGS)) {
                 // Получаем набор флагов, который, как ожидается, является Set<EntityFlag>
                val flags = metadata.get(EntityDataTypes.FLAGS)

                // Убедимся, что флаги - это Set<EntityFlag> и они не null
                if (flags is Set<*> && flags != null) { // Проверяем тип
                     val entityFlags = flags as Set<EntityFlag> // Безопасное приведение типов

                     // **ОБХОД ОШИБКИ 'val cannot be reassigned'**
                     // Вместо EnumSet.copyOf(entityFlags).apply { remove(...) }, создаем новый EnumSet вручную
                     val modifiedFlags = EnumSet.noneOf(EntityFlag::class.java) // Создаем новый EnumSet для EntityFlag
                     // Копируем все флаги из оригинального набора, кроме EntityFlag.CAN_FLY
                     for (flag in entityFlags) {
                         if (flag != EntityFlag.CAN_FLY) {
                             modifiedFlags.add(flag)
                         }
                     }

                     // Записываем модифицированный набор флагов обратно в метаданные
                     // Этот код остался без изменений из предыдущих попыток
                     // Проверяем, были ли изменения (опционально)
                     if (modifiedFlags != entityFlags) { // Проверка может работать не всегда
                         metadata.put(EntityDataTypes.FLAGS, modifiedFlags) // Записываем измененный набор
                     } else {
                          // Даже если набор не изменился (например, CAN_FLY не было),
                          // можем всегда записывать modifiedFlags обратно, или не делать ничего.
                          // Давайте запишем modifiedFlags обратно на всякий случай.
                          metadata.put(EntityDataTypes.FLAGS, modifiedFlags)
                     }
                 }
            }
        }
        // Блоки MovePlayerPacket, onValueChange, log.debug отсутствуют.
    }
    // Методы onValueChange и log.debug отсутствуют.
}
