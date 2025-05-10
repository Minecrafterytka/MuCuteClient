package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper // Возможно потребуется для BlockDefinition, но пока не добавляем
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData
// import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket // Убрал импорт LevelChunkPacket
// import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket // Убрал импорт LevelEventPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import java.util.concurrent.ConcurrentHashMap
// import kotlin.math.sqrt // sqrt не нужен, если используем distanceSquared

class BedBreaker : Module("BedBreaker", ModuleCategory.Misc) {

    // Настройки
    private var range by floatValue("range", 3.0f, 1.0f..6.0f) // Увеличил максимальный радиус
    private val autoSearch by boolValue("auto_search", true) // Автоматический поиск в области
    private val useFallback by boolValue("use_fallback", true) // Использовать заглушку (для отладки или если autoSearch = false)
    private val useManualCommand by boolValue("use_manual_command", false) // Ручной ввод через команду
    // ИСПРАВЛЕНИЕ 1: Добавляем широкий диапазон для floatValue координат
    private var bedX by floatValue("bedX", 0.0f, -30000000f..30000000f)
    private var bedY by floatValue("bedY", 0.0f, -30000000f..30000000f)
    private var bedZ by floatValue("bedZ", 0.0f, -30000000f..30000000f)

    private var lastBreakAttempt = 0L
    private val breakCooldown = 200L // Уменьшил задержку для более частых попыток
    private val blockMap = ConcurrentHashMap<Vector3i, String>() // Храним блоки, включая кровати (из UpdateBlockPacket)

    // Переменная для хранения позиции найденной кровати в режиме autoSearch/fallback
    private var targetBedPos: Vector3i? = null

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        // Если модуль не активен, пропускаем обработку всех пакетов, кроме команды включения/выключения
         if (!isEnabled) return

        val packet = interceptablePacket.packet

        // --- Обработка команд ---
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message.trim()
            if (message.startsWith(".bedbreak")) {
                interceptablePacket.intercept() // Перехватываем команду
                if (useManualCommand) {
                   handleManualBedBreakCommand(message)
                } else {
                   session.displayClientMessage("§l§b[BedBreaker] §r§cManual command mode is disabled. Toggle 'use_manual_command' in settings.")
                }
                return // Выходим после обработки команды
            }
        }

        // --- Отслеживание блоков (для blockMap) ---
        if (packet is UpdateBlockPacket) {
            val blockPos = packet.blockPosition
            // ИСПРАВЛЕНИЕ 2: Используем getIdentifier() вместо .identifier
            val blockId = packet.definition.getIdentifier() 
            
            // Проверяем, является ли блок кроватью (или частью кровати)
            if (blockId.contains("bed", ignoreCase = true)) {
                blockMap[blockPos] = blockId
                 // session.displayClientMessage("§l§b[BedBreaker] §r§aBed detected/updated at " + blockPos.x + " " + blockPos.y + " " + blockPos.z)
            } else {
                 if (blockMap.containsKey(blockPos)) {
                    blockMap.remove(blockPos)
                     // session.displayClientMessage("§l§b[BedBreaker] §r§7Block at " + blockPos.x + " " + blockPos.y + " " + blockPos.z + " is no longer a bed.")
                }
            }
        }

        // --- Логика ломания кровати ---
        if (packet is PlayerAuthInputPacket) {
            val currentTime = System.currentTimeMillis()
            // Проверяем кулдаун
            if (currentTime - lastBreakAttempt < breakCooldown) {
                return
            }

            val playerPosition = packet.position?.let { Vector3f.from(it.x, it.y, it.z) }
                ?: return

            // Определяем целевую позицию кровати
            targetBedPos = if (useManualCommand) {
                Vector3i.from(bedX.toInt(), bedY.toInt(), bedZ.toInt())
            } else {
                findNearestBed(playerPosition)
            }

            // Если целевая позиция кровати найдена
            if (targetBedPos != null) {
                // Рассчитываем расстояние до целевого блока (до центра блока)
                val distanceToTarget = playerPosition.distance(Vector3f.from(targetBedPos!!.x + 0.5f, targetBedPos!!.y + 0.5f, targetBedPos!!.z + 0.5f))

                // Проверяем, находится ли целевой блок в радиусе ломания
                if (distanceToTarget <= range) {
                    // --- Симуляция ломания: Отправляем START_BREAK и ABORT_BREAK ---

                    val startBreakAction = PlayerBlockActionData().apply {
                        action = PlayerActionType.START_BREAK
                        blockPosition = targetBedPos!!
                        face = 0
                        // ИСПРАВЛЕНИЕ 3: Удаляем resultPosition
                        // resultPosition = Vector3i.ZERO
                    }
                    val abortBreakAction = PlayerBlockActionData().apply {
                         action = PlayerActionType.ABORT_BREAK
                         blockPosition = targetBedPos!!
                         face = 0
                        // ИСПРАВЛЕНИЕ 3: Удаляем resultPosition
                        // resultPosition = Vector3i.ZERO
                    }

                    // Добавляем наши действия в список действий игрока для этого пакета
                    val blockActions = mutableListOf<PlayerBlockActionData>()
                    blockActions.add(startBreakAction)
                    blockActions.add(abortBreakAction)
                    
                    // ИСПРАВЛЕНИЕ 4: Используем setBlockActions() вместо прямого присваивания
                    packet.setBlockActions(blockActions)

                    // Убеждаемся, что флаг PERFORM_BLOCK_ACTIONS установлен
                    if (!packet.inputData.contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
                         packet.inputData.add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)
                    }

                    lastBreakAttempt = currentTime // Обновляем таймер кулдауна

                    // Отладочное сообщение
                    session.displayClientMessage("§l§b[BedBreaker] §r§aAttempting quick break of block at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z + " (distance: " + String.format("%.1f", distanceToTarget) + ", range: " + range + ")")

                } else {
                     // session.displayClientMessage("§l§b[BedBreaker] §r§cTarget block at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z + " is out of range (" + String.format("%.1f", distanceToTarget) + " > " + range + ")")
                }
            } else {
               // session.displayClientMessage("§l§b[BedBreaker] §r§cNo target block found in range or set.")
            }
        }
        
        // --- Закомментированные блоки ---
        // if (packet is LevelEventPacket) { ... }
        // if (packet is LevelChunkPacket) { ... }
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
        val searchCubeLimit = range.toInt() + 2
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
                                nearestBed = blockPos
                            }
                        }
                    }
                }
            }
        }

        // Если автоматический поиск не нашел И включена заглушка
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
         session.displayClientMessage("§l§b[BedBreaker] §r§aBedBreaker enabled.")
    }
}
