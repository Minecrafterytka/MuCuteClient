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
import org.cloudburstmc.math.vector.Vector2f // Для хранения 2D горизонтальной скорости
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes // Необходим для EntityDataTypes.FLAGS
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag // Необходим для EntityFlag.CAN_FLY
import java.util.EnumSet

class FlyModule : Module("fly", ModuleCategory.Motion) {
    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    // Переменная для хранения последней известной горизонтальной скорости клиента
    private var lastKnownHorizontalMotion: Vector2f = Vector2f.ZERO

    // Пакеты с обновлением способностей (включая флаги полета)
    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray()) // Все способности определены
            abilityValues.addAll(
                listOf( // Включаем более полный набор стандартных способностей + полет
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
            abilitiesSet.addAll(Ability.entries.toTypedArray()) // Все способности определены
            abilityValues.addAll(
                listOf( // Включаем более полный набор стандартных способностей (без полета)
                    Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS, Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS,
                    // Ability.MAY_FLY, // Удален из abilityValues
                    Ability.FLY_SPEED, // Оставляем, т.к. может использоваться для чего-то еще
                    Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
             // flySpeed здесь не устанавливаем
        })
    }

    private var canFly = false // Флаг состояния полета модуля

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // 1. Блокируем входящие RequestAbilityPacket (от сервера)
        if (packet is RequestAbilityPacket && packet.ability == Ability.FLYING) {
            interceptablePacket.intercept() // Блокируем запрос от сервера
            return
        }

        // 2. Блокируем входящие UpdateAbilitiesPacket (от сервера)
        if (packet is UpdateAbilitiesPacket) {
            interceptablePacket.intercept() // Блокируем обновление способностей от сервера
            return
        }

        // 3. Обработка исходящего PlayerAuthInputPacket (от клиента к серверу)
        if (packet is PlayerAuthInputPacket) {

            // **НОВЫЙ ШАГ:** Захватываем текущую горизонтальную скорость клиента из пакета
            // Это нужно делать всегда, независимо от того, включен ли модуль полета,
            // чтобы иметь актуальную скорость при его включении или нажатии JUMP/SNEAK.
            // ВНИМАНИЕ: packet.motion DEPRECATED и может быть ненадежным!
            // Возможно, потребуется вывод скорости из packet.delta, что сложнее.
            lastKnownHorizontalMotion = Vector2f.from(packet.motion.x, packet.motion.z)

            // 5. Активация/деактивация способностей полета у клиента
            // Эта логика должна выполняться ДО того, как мы обрабатываем движение или чистим флаги
            if (!canFly && isEnabled) {
                enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(enableFlyAbilitiesPacket) // Отправляем клиенту
                canFly = true
                 // log.debug("Fly module enabled. Sent enable abilities packet to client.") // Убран лог
            } else if (canFly && !isEnabled) {
                disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
                session.clientBound(disableFlyAbilitiesPacket) // Отправляем клиенту
                canFly = false
                 // log.debug("Fly module disabled. Sent disable abilities packet to client.") // Убран лог
                // После выключения, пакет PlayerAuthInputPacket все равно должен пройти к серверу
            }

            // Обработка, только если модуль включен
            if (isEnabled) {
                // 4. Чистим PlayerAuthInputPacket от флагов полета, но ОСТАВЛЯЕМ JUMPING/SNEAKING
                val originalInputData = packet.inputData // Получаем исходный набор (до модификации)

                val modifiedInputData = EnumSet.copyOf(originalInputData).apply {
                    remove(PlayerAuthInputData.START_FLYING) // Скрываем явный старт полета
                    remove(PlayerAuthInputData.STOP_FLYING)  // Скрываем явную остановку полета
                    // Оставляем JUMPING и SNEAKING для согласованности с вертикальным движением (по вашему предположению)
                    // remove(PlayerAuthInputData.JUMPING) // Удалена строка
                    // remove(PlayerAuthInputData.SNEAKING) // Удалена строка
                }

                // Создаем клон пакета с чистыми данными и заменяем его для отправки на сервер
                val modifiedPacket = packet.clone().apply {
                    inputData.clear()
                    inputData.addAll(modifiedInputData)
                    // Здесь используется packet.motion.x/z (из оригинального пакета)
                    // FIX: Убедитесь, что доступ к .x и .z осуществляется через свойства, а не методы .x()/.z()
                    // Ваша последняя версия кода уже использовала свойства .x и .z
                }
                interceptablePacket.packet = modifiedPacket // Заменяем пакет в цепочке обработки на модифицированный

                // 5. Управление вертикальным движением через SetEntityMotionPacket (для клиента)
                // Используем ОРИГИНАЛЬНЫЕ inputData для определения ввода для вертикального движения
                var verticalMotion = 0f
                if (originalInputData.contains(PlayerAuthInputData.JUMPING)) { // Используем originalInputData
                    verticalMotion = flySpeed
                } else if (originalInputData.contains(PlayerAuthInputData.SNEAKING)) { // Используем originalInputData
                    verticalMotion = -flySpeed
                }

                // Если обнаружено вертикальное движение, отправляем пакет SetEntityMotionPacket клиенту
                if (verticalMotion != 0f) {
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        // **ИЗМЕНЕНИЕ:** Объединяем ПОСЛЕДНЮЮ ИЗВЕСТНУЮ ГОРИЗОНТАЛЬНУЮ СКОРОСТЬ
                        // с вычисленной ВЕРТИКАЛЬНОЙ скоростью.
                        motion = Vector3f.from(lastKnownHorizontalMotion.x, verticalMotion, lastKnownHorizontalMotion.y)
                    }
                    session.clientBound(motionPacket) // Отправляем ТОЛЬКО клиенту
                     // log.debug("Sent SetEntityMotionPacket clientBound with motion: ${motionPacket.motion}") // Убран лог
                }
            }
             // Пакет PlayerAuthInputPacket (возможно модифицированный) отправляется дальше к серверу.
        }

        // 6. Дополнительная проверка: скрытие флага CAN_FLY в метаданных (входящий от сервера)
        if (packet is SetEntityDataPacket) {
            val flags = packet.metadata.get(EntityDataTypes.FLAGS) ?: return
            val modifiedFlags = EnumSet.copyOf(flags).apply {
                remove(EntityFlag.CAN_FLY) // Удаляем флаг CAN_FLY
            }
             packet.metadata.put(EntityDataTypes.FLAGS, modifiedFlags) // Записываем измененную копию
        }
        // Блоки обработки MovePlayerPacket и onValueChange отсутствуют.
    }
    // Метод onValueChange отсутствует.
}
