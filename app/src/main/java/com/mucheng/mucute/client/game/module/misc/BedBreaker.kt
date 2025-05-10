package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt // Добавляем импорт для sqrt, если нужна точная дистанция (для отладки)

class BedBreaker : Module("BedBreaker", ModuleCategory.Misc) {

    // Настройки
    private var range by floatValue("range", 3.0f, 1.0f..6.0f) // Увеличил максимальный радиус
    private val autoSearch by boolValue("auto_search", true) // Автоматический поиск в области
    private val useFallback by boolValue("use_fallback", true) // Использовать заглушку (для отладки или если autoSearch = false)
    private val useManualCommand by boolValue("use_manual_command", false) // Ручной ввод через команду
    private var bedX by floatValue("bedX", 0.0f)
    private var bedY by floatValue("bedY", 0.0f)
    private var bedZ by floatValue("bedZ", 0.0f)

    private var lastBreakAttempt = 0L
    private val breakCooldown = 200L // Уменьшил задержку для более частых попыток
    private val blockMap = ConcurrentHashMap<Vector3i, String>() // Храним блоки, включая кровати (из UpdateBlockPacket)

    // Переменная для хранения позиции найденной кровати в режиме autoSearch/fallback
    private var targetBedPos: Vector3i? = null

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        // Если модуль не активен, пропускаем обработку всех пакетов, кроме команды включения/выключения (которая обрабатывается движком модулей)
         if (!isEnabled) return

        val packet = interceptablePacket.packet

        // --- Обработка команд ---
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message.trim()
            if (message.startsWith(".bedbreak")) {
                interceptablePacket.intercept() // Перехватываем команду, чтобы она не ушла на сервер
                if (useManualCommand) {
                   handleManualBedBreakCommand(message)
                } else {
                   session.displayClientMessage("§l§b[BedBreaker] §r§cManual command mode is disabled. Toggle 'use_manual_command' in settings.")
                }
                return // Выходим после обработки команды
            }
        }

        // --- Отслеживание блоков (для blockMap) ---
        // Это полезно для режима autoSearch, но blockMap неполный без обработки LevelChunkPacket
        if (packet is UpdateBlockPacket) {
            val blockPos = packet.blockPosition
            val blockId = packet.definition.getIdentifier() // Получаем строковый идентификатор блока
            
            // Проверяем, является ли блок кроватью (или частью кровати)
            if (blockId.contains("bed", ignoreCase = true)) {
                // Добавляем или обновляем кровать в мапе
                blockMap[blockPos] = blockId
                 // session.displayClientMessage("§l§b[BedBreaker] §r§aBed detected/updated at " + blockPos.x + " " + blockPos.y + " " + blockPos.z) // Отладочное сообщение, можно отключить
            } else {
                // Если блок с этой позиции больше не кровать (например, сломан или заменен), удаляем его из мапы
                 // Проверяем, был ли блок кроватью, прежде чем сообщать об удалении
                if (blockMap.containsKey(blockPos)) {
                    blockMap.remove(blockPos)
                     // session.displayClientMessage("§l§b[BedBreaker] §r§7Block at " + blockPos.x + " " + blockPos.y + " " + blockPos.z + " is no longer a bed.") // Отладочное сообщение
                }
            }
        }

        // --- Логика ломания кровати ---
        // Основную логику ломания запускаем при отправке PlayerAuthInputPacket игрока
        if (packet is PlayerAuthInputPacket) {
            val currentTime = System.currentTimeMillis()
            // Проверяем кулдаун перед попыткой ломания
            if (currentTime - lastBreakAttempt < breakCooldown) {
                return // Выходим, если кулдаун еще не прошел
            }

            // Получаем позицию игрока из пакета
            val playerPosition = packet.position?.let { Vector3f.from(it.x, it.y, it.z) }
                ?: return // Если позиция игрока недоступна, выходим

            // Определяем целевую позицию кровати
            targetBedPos = if (useManualCommand) {
                // В ручном режиме берем координаты из настроек
                Vector3i.from(bedX.toInt(), bedY.toInt(), bedZ.toInt())
            } else {
                // В автоматическом режиме или с заглушкой - ищем ближайшую кровать
                findNearestBed(playerPosition)
            }

            // Если целевая позиция кровати найдена
            if (targetBedPos != null) {
                // Рассчитываем расстояние до целевого блока (до центра блока)
                val distanceToTarget = playerPosition.distance(Vector3f.from(targetBedPos!!.x + 0.5f, targetBedPos!!.y + 0.5f, targetBedPos!!.z + 0.5f))

                // Проверяем, находится ли целевой блок в радиусе ломания
                if (distanceToTarget <= range) {
                    // --- Симуляция ломания: Отправляем START_BREAK и ABORT_BREAK ---
                    // Эта связка часто симулирует быструю попытку ломания.

                    val startBreakAction = PlayerBlockActionData().apply {
                        action = PlayerActionType.START_BREAK // Начать ломать
                        blockPosition = targetBedPos!! // Позиция целевого блока
                        face = 0 // Направление ломания (0 = Bottom, обычно не критично для старта)
                        resultPosition = Vector3i.ZERO // Не используется для START/ABORT
                    }
                    val abortBreakAction = PlayerBlockActionData().apply {
                        action = PlayerActionType.ABORT_BREAK // Отменить/завершить ломание
                        blockPosition = targetBedPos!! // Та же позиция
                        face = 0 // Направление (должно совпадать со START, если используется)
                        resultPosition = Vector3i.ZERO
                    }

                    // Добавляем наши действия в список действий игрока для этого пакета
                    // К пакету PlayerAuthInputPacket можно добавить список PlayerBlockActionData
                    // Важно: убедиться, что флаг PERFORM_BLOCK_ACTIONS установлен
                    
                    // Создаем новый список действий, включающий наши команды
                    val blockActions = mutableListOf<PlayerBlockActionData>()
                    // Можно добавить существующие действия пакета, если нужно
                    // blockActions.addAll(packet.blockActions)
                    blockActions.add(startBreakAction)
                    blockActions.add(abortBreakAction)
                    
                    packet.blockActions = blockActions // Присваиваем измененный список обратно пакету

                    // Убеждаемся, что флаг PERFORM_BLOCK_ACTIONS установлен
                    // Это говорит серверу, что в этом пакете есть действия с блоками, которые нужно обработать
                    if (!packet.inputData.contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
                         packet.inputData.add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)
                    }

                    lastBreakAttempt = currentTime // Обновляем таймер кулдауна

                    // Отладочное сообщение о попытке ломания
                    session.displayClientMessage("§l§b[BedBreaker] §r§aAttempting quick break of block at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z + " (distance: " + String.format("%.1f", distanceToTarget) + ", range: " + range + ")")

                } else {
                    // Целевой блок вне радиуса
                    // Отладочное сообщение (можно сделать не таким частым)
                     // session.displayClientMessage("§l§b[BedBreaker] §r§cTarget block at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z + " is out of range (" + String.format("%.1f", distanceToTarget) + " > " + range + ")")
                }
            } else {
               // Целевой блок не найден (в режиме autoSearch/fallback)
               // Отладочное сообщение (можно сделать не таким частым)
                // session.displayClientMessage("§l§b[BedBreaker] §r§cNo target block found in range or set.")
            }
        }
        
        // --- Перехват LevelEventPacket и LevelChunkPacket ---
        // Если вы не используете их для чего-то специфичного в модуле,
        // эти блоки кода можно убрать, чтобы не засорять отладку.
        /*
        if (packet is LevelEventPacket) {
             if (packet.type.toString().contains("DESTROY", ignoreCase = true) ||
                 packet.type.toString().contains("BREAK", ignoreCase = true)) {
                 // session.displayClientMessage("§l§b[BedBreaker] §r§7Block break event at " + packet.position.x + " " + packet.position.y + " " + packet.position.z)
             }
         }
        if (packet is LevelChunkPacket) {
             // session.displayClientMessage("§l§b[BedBreaker] §r§7Received chunk (" + packet.chunkX + ", " + packet.chunkZ + "), subChunks: " + packet.subChunksLength)
         }
        */
    }

    // Обработка команды ручного ввода координат кровати
    private fun handleManualBedBreakCommand(message: String) {
        val args = message.split(" ")
        if (args.size != 4) {
            session.displayClientMessage("§l§b[BedBreaker] §r§cUsage: .bedbreak <x> <y> <z>")
            return
        }

        try {
            bedX = args[1].toFloat()
            bedY = args[2].toFloat()
            bedZ = args[3].toFloat()
            // Убеждаемся, что цель обновлена сразу
            targetBedPos = Vector3i.from(bedX.toInt(), bedY.toInt(), bedZ.toInt())
            session.displayClientMessage(
                "§l§b[BedBreaker] §r§7Targeting bed at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z
            )
        } catch (e: NumberFormatException) {
            session.displayClientMessage("§l§b[BedBreaker] §r§cInvalid coordinates")
        }
    }

    // Функция для поиска ближайшей кровати или использования заглушки
    private fun findNearestBed(playerPosition: Vector3f): Vector3i? {
        // Если автоматический поиск отключен, используем только заглушку (если включена)
        if (!autoSearch) {
            return if (useFallback) getFallbackBedPosition(playerPosition) else null
        }

        // Если автоматический поиск включен - ищем в blockMap
        var nearestBed: Vector3i? = null
        var minDistanceSq = Float.MAX_VALUE // Используем квадрат расстояния для сравнения

        // Ищем в блоках из blockMap, которые находятся в кубе вокруг игрока
        // Размер куба берем чуть больше радиуса, чтобы покрыть float радиус
        val searchCubeLimit = range.toInt() + 2 // Увеличим еще на всякий случай
        val playerBlockPos = Vector3i.from(playerPosition.x.toInt(), playerPosition.y.toInt(), playerPosition.z.toInt())

        // Итерируем по кубу и проверяем blockMap
        // NOTE: blockMap может быть неполным без обработки LevelChunkPacket!
        for (x in -searchCubeLimit..searchCubeLimit) {
            for (y in -searchCubeLimit..searchCubeLimit) {
                for (z in -searchCubeLimit..searchCubeLimit) {
                    val blockPos = playerBlockPos.add(x, y, z)
                    val blockId = blockMap[blockPos] // Проверяем наличие в мапе и получаем ID
                    
                    // Проверяем, что блок есть в мапе и это кровать
                    if (blockId?.contains("bed", ignoreCase = true) == true) {
                         // Рассчитываем квадрат расстояния до центра блока
                        val blockCenterPos = Vector3f.from(blockPos.x + 0.5f, blockPos.y + 0.5f, blockPos.z + 0.5f)
                        val distanceSq = playerPosition.distanceSquared(blockCenterPos)
                        
                        // Если блок в радиусе ломания
                        if (distanceSq <= range * range) {
                            // Если это ближайшая кровать из найденных на данный момент
                            if (distanceSq < minDistanceSq) {
                                minDistanceSq = distanceSq
                                nearestBed = blockPos // Сохраняем эту позицию как ближайшую
                            }
                        }
                    }
                }
            }
        }

        // Если автоматический поиск не нашел (nearestBed все еще null) И включена заглушка
        if (nearestBed == null && useFallback) {
            return getFallbackBedPosition(playerPosition)
        }

        // Возвращаем либо найденную ближайшую кровать, либо позицию заглушки, либо null
        return nearestBed
    }

    // Вспомогательная функция для получения позиции заглушки кровати
    private fun getFallbackBedPosition(playerPosition: Vector3f): Vector3i? {
         // Пример: позиция заглушки - 2 блока вперед от игрока (или любое другое фиксированное смещение)
         val testBedPos = Vector3i.from(
             playerPosition.x.toInt(),
             playerPosition.y.toInt(),
             playerPosition.z.toInt() + 2
         )
         // Проверяем, что заглушка находится в радиусе ломания
         val distance = playerPosition.distance(Vector3f.from(testBedPos.x + 0.5f, testBedPos.y + 0.5f, testBedPos.z + 0.5f))
         if (distance <= range) {
             // Сообщаем об использовании заглушки только при необходимости
              // session.displayClientMessage("§l§b[BedBreaker] §r§7Using fallback bed position at " + testBedPos.x + " " + testBedPos.y + " " + testBedPos.z)
             return testBedPos
         }
         return null // Заглушка вне радиуса ломания
    }

    // Дополнительно: метод для очистки blockMap, например, при отключении модуля
    override fun onDisabled() {
        super.onDisabled()
        blockMap.clear() // Очищаем карту блоков при отключении
        targetBedPos = null // Сбрасываем целевую позицию
         // session.displayClientMessage("§l§b[BedBreaker] §r§7Block map cleared.")
    }

    override fun onEnabled() {
        super.onEnabled()
        // Возможно, здесь стоит добавить логику для попытки первичного заполнения blockMap,
        // если это возможно без полной обработки чанков. Но скорее всего, UpdateBlockPacket
        // будет единственным источником для blockMap в этой реализации.
         session.displayClientMessage("§l§b[BedBreaker] §r§aBedBreaker enabled.")
    }
}
