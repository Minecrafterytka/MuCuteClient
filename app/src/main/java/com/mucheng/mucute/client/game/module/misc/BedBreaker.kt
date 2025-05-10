package com.mucheng.mucute.client.game.module.misc

import com.mucheng.mucute.client.game.InterceptablePacket
import com.mucheng.mucute.client.game.Module
import com.mucheng.mucute.client.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.math.vector.Vector3i
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import java.util.concurrent.ConcurrentHashMap

class BedBreaker : Module("BedBreaker", ModuleCategory.Misc) {

    // Настройки
    private var range by floatValue("range", 3.0f, 1.0f..6.0f)
    private val autoSearch by boolValue("auto_search", true)
    private val useFallback by boolValue("use_fallback", true)
    private val useManualCommand by boolValue("use_manual_command", false)
    private var bedX by floatValue("bedX", 0.0f, -30000000f..30000000f)
    private var bedY by floatValue("bedY", 0.0f, -30000000f..30000000f)
    private var bedZ by floatValue("bedZ", 0.0f, -30000000f..30000000f)

    private var lastBreakAttempt = 0L
    private val breakCooldown = 200L
    private val blockMap = ConcurrentHashMap<Vector3i, String>()

    private var targetBedPos: Vector3i? = null

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
         if (!isEnabled) return

        val packet = interceptablePacket.packet

        // --- Обработка команд ---
        if (packet is TextPacket && packet.type == TextPacket.Type.CHAT) {
            val message = packet.message.trim()
            if (message.startsWith(".bedbreak")) {
                interceptablePacket.intercept()
                if (useManualCommand) {
                   handleManualBedBreakCommand(message)
                } else {
                   session.displayClientMessage("§l§b[BedBreaker] §r§cManual command mode is disabled. Toggle 'use_manual_command' in settings.")
                }
                return
            }
        }

        // --- Отслеживание блоков (для blockMap) ---
        if (packet is UpdateBlockPacket) {
            val blockPos = packet.blockPosition
            // ИСПРАВЛЕНИЕ для getIdentifier: Попытка использовать getId()
            val blockId = packet.definition.getId() // Попытка получить идентификатор через getId()
            
            if (blockId != null && blockId.contains("bed", ignoreCase = true)) { // Проверяем, что blockId не null
                blockMap[blockPos] = blockId
            } else {
                 if (blockMap.containsKey(blockPos)) {
                    blockMap.remove(blockPos)
                }
            }
        }

        // --- Логика ломания кровати ---
        if (packet is PlayerAuthInputPacket) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBreakAttempt < breakCooldown) {
                return
            }

            val playerPosition = packet.position?.let { Vector3f.from(it.x, it.y, it.z) }
                ?: return

            targetBedPos = if (useManualCommand) {
                Vector3i.from(bedX.toInt(), bedY.toInt(), bedZ.toInt())
            } else {
                findNearestBed(playerPosition)
            }

            if (targetBedPos != null) {
                val distanceToTarget = playerPosition.distance(Vector3f.from(targetBedPos!!.x + 0.5f, targetBedPos!!.y + 0.5f, targetBedPos!!.z + 0.5f))

                if (distanceToTarget <= range) {
                    val startBreakAction = PlayerBlockActionData().apply {
                        action = PlayerActionType.START_BREAK
                        blockPosition = targetBedPos!!
                        face = 0
                        // resultPosition удален
                    }
                    val abortBreakAction = PlayerBlockActionData().apply {
                         action = PlayerActionType.ABORT_BREAK
                         blockPosition = targetBedPos!!
                         face = 0
                        // resultPosition удален
                    }

                    // ИСПРАВЛЕНИЕ для blockActions: Получаем СУЩЕСТВУЮЩИЙ список и добавляем в него
                    // Список PlayerBlockActionData в пакете называется playerActions
                    packet.getPlayerActions().add(startBreakAction) // Добавляем действие START_BREAK
                    packet.getPlayerActions().add(abortBreakAction) // Добавляем действие ABORT_BREAK
                    
                    // Убеждаемся, что флаг PERFORM_BLOCK_ACTIONS установлен
                    if (!packet.inputData.contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
                         packet.inputData.add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)
                    }

                    lastBreakAttempt = currentTime

                    session.displayClientMessage("§l§b[BedBreaker] §r§aAttempting quick break of block at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z + " (distance: " + String.format("%.1f", distanceToTarget) + ", range: " + range + ")")

                } else {
                     // session.displayClientMessage("§l§b[BedBreaker] §r§cTarget block at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z + " is out of range (" + String.format("%.1f", distanceToTarget) + " > " + range + ")")
                }
            } else {
               // session.displayClientMessage("§l§b[BedBreaker] §r§cNo target block found in range or set.")
            }
        }
    }

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
            targetBedPos = Vector3i.from(bedX.toInt(), bedY.toInt(), bedZ.toInt())
            session.displayClientMessage(
                "§l§b[BedBreaker] §r§7Targeting bed at " + targetBedPos!!.x + " " + targetBedPos!!.y + " " + targetBedPos!!.z
            )
        } catch (e: NumberFormatException) {
            session.displayClientMessage("§l§b[BedBreaker] §r§cInvalid coordinates")
        }
    }

    private fun findNearestBed(playerPosition: Vector3f): Vector3i? {
        if (!autoSearch) {
            return if (useFallback) getFallbackBedPosition(playerPosition) else null
        }

        var nearestBed: Vector3i? = null
        var minDistanceSq = Float.MAX_VALUE

        val searchCubeLimit = range.toInt() + 2
        val playerBlockPos = Vector3i.from(playerPosition.x.toInt(), playerPosition.y.toInt(), playerPosition.z.toInt())

        for (x in -searchCubeLimit..searchCubeLimit) {
            for (y in -searchCubeLimit..searchCubeLimit) {
                for (z in -searchCubeLimit..searchCubeLimit) {
                    val blockPos = playerBlockPos.add(x, y, z)
                    val blockId = blockMap[blockPos]
                    
                    if (blockId != null && blockId.contains("bed", ignoreCase = true)) { // Проверяем, что blockId не null
                        val blockCenterPos = Vector3f.from(blockPos.x + 0.5f, blockPos.y + 0.5f, blockPos.z + 0.5f)
                        val distanceSq = playerPosition.distanceSquared(blockCenterPos)
                        
                        if (distanceSq <= range * range) {
                            if (distanceSq < minDistanceSq) {
                                minDistanceSq = distanceSq
                                nearestBed = blockPos
                            }
                        }
                    }
                }
            }
        }

        if (nearestBed == null && useFallback) {
            return getFallbackBedPosition(playerPosition)
        }

        return nearestBed
    }

    private fun getFallbackBedPosition(playerPosition: Vector3f): Vector3i? {
         val testBedPos = Vector3i.from(
             playerPosition.x.toInt(),
             playerPosition.y.toInt(),
             playerPosition.z.toInt() + 2
         )
         val distance = playerPosition.distance(Vector3f.from(testBedPos.x + 0.5f, testBedPos.y + 0.5f, testBedPos.z + 0.5f))
         if (distance <= range) {
             return testBedPos
         }
         return null
    }

    override fun onDisabled() {
        super.onDisabled()
        blockMap.clear()
        targetBedPos = null
    }

    override fun onEnabled() {
        super.onEnabled()
        session.displayClientMessage("§l§b[BedBreaker] §r§aBedBreaker enabled.")
    }
}
