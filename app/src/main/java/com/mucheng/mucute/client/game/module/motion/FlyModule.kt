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
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket // Возможно пригодится для старых версий

class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    // Пакет для включения способностей полета на КЛИЕНТЕ
    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        // Установка прав для обхода клиентских проверок
        playerPermission = PlayerPermission.OPERATOR
        commandPermission = CommandPermission.OWNER
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            // Добавляем все способности, а потом явно указываем, какие значения переопределяем
            abilitiesSet.addAll(Ability.entries.toTypedArray()) // Включаем все флаги способностей
            abilityValues.addAll(
                arrayOf(
                    // Эти способности обычно есть или должны быть включены
                    Ability.BUILD,
                    Ability.MINE,
                    Ability.DOORS_AND_SWITCHES,
                    Ability.OPEN_CONTAINERS,
                    Ability.ATTACK_PLAYERS,
                    Ability.ATTACK_MOBS,
                    Ability.OPERATOR_COMMANDS,
                    // Ключевая способность для клиента
                    Ability.MAY_FLY,
                    Ability.FLY_SPEED,
                    Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f // Оставляем стандартную скорость ходьбы
            flySpeed = this@FlyModule.flySpeed // Устанавливаем скорость полета
        })
    }

    // Пакет для выключения способностей полета на КЛИЕНТЕ
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
                    // Исключаем MAY_FLY
                    Ability.FLY_SPEED, // Может понадобиться для скорости ходьбы? Или установить walkSpeed?
                    Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f // Возвращаем стандартную скорость ходьбы
            // flySpeed не указываем в abilityValues, или ставим 0, чтобы выключить на клиенте
        })
    }

    private var canFlyClient = false // Флаг, показывающий, включили ли мы полет на клиенте

    override fun onEnable() {
        // Отправляем пакет включения способностей сразу при включении модуля
        // Вместо того, чтобы ждать PlayerAuthInputPacket
        enableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
        session.clientBound(enableFlyAbabilitiesPacket)
        canFlyClient = true
    }

    override fun onDisable() {
        // Отправляем пакет выключения способностей сразу при выключении модуля
        disableFlyAbilitiesPacket.uniqueEntityId = session.localPlayer.uniqueEntityId
        session.clientBound(disableFlyAbilitiesPacket)
        canFlyClient = false
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Всегда перехватываем пакеты запроса способностей и обновления способностей
        // Они могут приходить от сервера и мешать нашим клиентским способностям
        if (packet is RequestAbilityPacket || packet is UpdateAbilitiesPacket) {
            interceptablePacket.intercept()
            return
        }

        // Обрабатываем пакеты ввода игрока, которые идут на сервер
        if (packet is PlayerAuthInputPacket) {

            // Если модуль включен, скрываем флаги полета от сервера
            if (isEnabled) {
                // Удаляем из набора ввода флаги, связанные с полетом/парением
                // Сервер, вероятно, проверяет именно их для определения полета
                packet.inputData.remove(PlayerAuthInputData.START_FLYING)
                packet.inputData.remove(PlayerAuthInputData.STOP_FLYING)
                packet.inputData.remove(PlayerAuthInputData.ASCEND)
                packet.inputData.remove(PlayerAuthInputData.DESCEND)
                packet.inputData.remove(PlayerAuthInputData.CHANGE_HEIGHT) // Тоже может быть связано с полетом
                packet.inputData.remove(PlayerAuthInputData.START_GLIDING)
                packet.inputData.remove(PlayerAuthInputData.STOP_GLIDING)

                // Опционально: можно попробовать установить флаг isOnGround=true
                // Но в PlayerAuthInputPacket такого прямого флага нет.
                // Сервер может вычислять его по другим данным (позиция, движение, дельта).
                // Простейший античит может не проверять это детально, а смотреть только флаги START_FLYING и т.п.
                // Если сервер кикает из-за 'Flying is not enabled', это почти наверняка из-за этих флагов.

                // Обработка вертикального движения (как и раньше, только на клиенте)
                var verticalMotion = 0f

                // Проверяем клиентские флаги ввода для определения вертикального движения
                // Важно: эти флаги (JUMPING, SNEAKING) мы НЕ удаляем из пакета на сервер,
                // так как это стандартные действия игрока, которые сервер ожидает.
                if (packet.inputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMotion = flySpeed
                } else if (packet.inputData.contains(PlayerAuthInputData.SNEAKING)) {
                    verticalMotion = -flySpeed
                }

                if (verticalMotion != 0f) {
                    // Отправляем пакет движения ТОЛЬКО клиенту
                    val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = Vector3f.from(0f, verticalMotion, 0f)
                    }
                    session.clientBound(motionPacket)

                    // Опционально и рискованно: можно попробовать скорректировать motion/delta в PlayerAuthInputPacket
                    // отправляемом на сервер, чтобы скрыть вертикальное движение.
                    // Но это может вызвать другие проблемы (рассинхронизация, другие проверки античита).
                    // Начнем с простого: только фильтруем флаги.
                    // packet.motion = Vector2f.ZERO // Сбросить горизонтальное движение? Нет, это сломает ходьбу.
                    // packet.delta = packet.delta.to().withY(0f).from() // Обнулить вертикальную дельту? Очень рискованно.

                } else {
                    // Если нет вертикального движения по кнопкам, можно принудительно установить вертикальное движение в 0 на клиенте,
                    // чтобы остановить полет, если игрок отпустил кнопки.
                     val motionPacket = SetEntityMotionPacket().apply {
                        runtimeEntityId = session.localPlayer.runtimeEntityId
                        motion = Vector3f.ZERO // Останавливаем вертикальное движение на клиенте
                    }
                    session.clientBound(motionPacket)
                }
                
                // Позволяем МОДИФИЦИРОВАННОМУ пакету PlayerAuthInputPacket пройти на сервер
                // interceptablePacket.allow() // Это действие по умолчанию, если не вызвать intercept()

            } else {
                // Если модуль выключен, позволяем пакету пройти без изменений
                // interceptablePacket.allow()
            }
            // Если мы не вызвали intercept(), пакет автоматически будет отправлен
            return
        }
        
        // Если это MovePlayerPacket (для старых версий или других сущностей),
        // и он относится к нашему игроку, мы также можем попробовать его модифицировать,
        // чтобы скрыть полет (например, установить onGround = true),
        // но PlayerAuthInputPacket является основным для новых версий.
        // Для простоты и ориентации на новые версии, пока оставим только работу с PlayerAuthInputPacket.
        /*
        if (packet is MovePlayerPacket && packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
            if (isEnabled) {
                 // В старых версиях MovePlayerPacket мог содержать onGround
                 packet.onGround = true // Пытаемся скрыть, что мы не на земле
                 // Корректировка позиции/движения здесь тоже очень сложна
            }
            // Позволяем модифицированному пакету пройти
            return
        }
        */

        // Если пакет не обрабатывается модулем, позволяем ему пройти
        // interceptablePacket.allow() // Это поведение по умолчанию
    }
}
