package com.mucheng.mucute.client.game.module.motion

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory

import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket

import org.cloudburstmc.protocol.bedrock.data.Ability
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer // Этот импорт теперь должен работать
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission

import org.cloudburstmc.math.vector.Vector3f

import java.util.EnumSet // Убедимся, что этот импорт есть


// Удалены импорты для SetEntityDataPacket и связанных типов, т.к. блок удален
// import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket
// import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes
// import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag


class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        // Паттерн с AbilityLayer.add().apply{} теперь должен компилироваться
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                listOf(
                    Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS, Ability.MAY_FLY, Ability.FLY_SPEED, Ability.WALK_SPEED // MAY_FLY включен для клиента
                )
            )
            walkSpeed = 0.1f
            flySpeed = this@FlyModule.flySpeed
        })
    }

    private val disableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        // Паттерн с AbilityLayer.add().apply{} теперь должен компилироваться
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                listOf(
                    Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS,
                    // Ability.MAY_FLY, // MAY_FLY убран для отключения
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

        // Блокируем входящие запросы на активацию полета от сервера (для FLYING)
        // и входящие обновления способностей от сервера
        if ((packet is RequestAbilityPacket && packet.ability == Ability.FLYING) || packet is UpdateAbilitiesPacket) {
             if (isEnabled && canFly) { // Блокируем только если модуль включен и полет активирован нами
                 interceptablePacket.intercept()
                 return
             }
             return // Пропускаем, если не наш пакет или модуль выключен
        }

        if (packet is PlayerAuthInputPacket) {
            // **ВКЛЮЧЕНА/ВЫКЛЮЧЕНА** способности полета (отправляем клиенту)
            // Размещаем эту логику здесь, до модификации пакета для сервера
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

            // Обработка пакета только если модуль включен
            if (isEnabled) {
                // **ИСПРАВЛЕНО:** Правильно скрываем флаги полета из PlayerAuthInputPacket перед отправкой на сервер
                // Используем EnumSet.copyOf().apply{} - теперь должно компилироваться!
                // УБРАНЫ ОШИБОЧНЫЕ ПРЯМЫЕ ВЫЗОВЫ packet.inputData.remove()
                val filteredInputData = EnumSet.copyOf(packet.inputData).apply {
                    remove(PlayerAuthInputData.START_FLYING)
                    remove(PlayerAuthInputData.STOP_FLYING)
                    // Оставляем JUMPING и SNEAKING для согласованности с вертикальным движением
                }

                // **ИСПРАВЛЕНО:** Создаем клон пакета с модифицированными данными и ПРАВИЛЬНО заменяем исходный пакет для отправки на сервер
                 val modifiedPacket = packet.clone().apply {
                     inputData.clear() // Очищаем оригинальный набор в клоне
                     inputData.addAll(filteredInputData) // Добавляем модифицированный набор
                     // Остальные данные пакета (позиция, движение, вращение) остаются как в оригинале
                 }
                 // **ПРАВИЛЬНАЯ маршрутизация пакета:** Заменяем оригинальный пакет на модифицированный
                 interceptablePacket.packet = modifiedPacket


                // Handle vertical movement when enabled (как в вашей основе)
                var verticalMotion = 0f
                // Space for up, Shift for down
                // Используем originalInputData для проверки ввода, т.к. packet.inputData в modifiedPacket уже изменен
                // Хотя можно использовать и filteredInputData, т.к. JUMPING/SNEAKING не удаляются
                if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) { // Можно использовать packet.inputData т.к. contains не меняет набор
                    verticalMotion = flySpeed
                } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) { // Можно использовать packet.inputData т.к. contains не меняет набор
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
            }
            // Пакет PlayerAuthInputPacket (теперь с очищенными флагами полета, если модуль включен) отправляется дальше к серверу.
            // Благодаря interceptablePacket.packet = modifiedPacket, отправится модифицированный пакет.
        }

        // Удален блок SetEntityDataPacket, который вызывал ошибки компиляции
    }
}
