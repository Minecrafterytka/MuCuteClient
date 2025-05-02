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
// SetEntityMotionPacket больше не нужен для отправки клиенту
// import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket

// Удаляем MovePlayerPacket, т.к. не можем надежно перехватить его от сервера в этой структуре
// import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket

class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    // Пакеты для включения/выключения полета на стороне клиента (чтобы работало управление)
    private val enableFlyAbilitiesPacket = UpdateAbilitiesPacket().apply {
        playerPermission = PlayerPermission.OPERATOR // Или PLAYER/MEMBER
        commandPermission = CommandPermission.OWNER // Или MEMBER/ANY
        abilityLayers.add(AbilityLayer().apply {
            layerType = AbilityLayer.Type.BASE
            abilitiesSet.addAll(Ability.entries.toTypedArray())
            abilityValues.addAll(
                arrayOf(
                    Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES, Ability.OPEN_CONTAINERS,
                    Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS, Ability.OPERATOR_COMMANDS,
                    Ability.MAY_FLY, // Включаем полет для клиента
                    Ability.FLY_SPEED, Ability.WALK_SPEED
                )
            )
            walkSpeed = 0.1f
            // Скорость полета будет установлена динамически при отправке
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
                     Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES, Ability.OPEN_CONTAINERS,
                     Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS, Ability.OPERATOR_COMMANDS,
                     // Ability.MAY_FLY не добавляем
                     Ability.FLY_SPEED, Ability.WALK_SPEED
                 )
             )
            walkSpeed = 0.1f
         })
    }

    // Флаг для отслеживания, отправили ли мы клиенту пакет, разрешающий полет
    // Используется для отправки пакета способности только один раз при включении модуля
    private var clientFlightAbilitySent = false // Переименован из canFly для ясности

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Логика включения/выключения клиентской способности полета основана на состоянии модуля (isEnabled)
        // и нашем флаге отправки пакета (clientFlightAbilitySent).
        // Эта логика остается внутри beforePacketBound, как в оригинальной структуре.
        if (isEnabled && !clientFlightAbilitySent && session?.localPlayer != null) {
             val packetToSend = enableFlyAbilitiesPacket.clone()
             packetToSend.uniqueEntityId = session.localPlayer.uniqueEntityId
             // Устанавливаем скорость полета динамически из настройки модуля
             packetToSend.abilityLayers.find { it.layerType == AbilityLayer.Type.BASE }?.flySpeed = this.flySpeed
             session.clientBound(packetToSend) // Отправляем пакет способности КЛИЕНТУ
             clientFlightAbilitySent = true
             // Оригинальный код имел 'canFly = true' здесь
        } else if (!isEnabled && clientFlightAbilitySent && session?.localPlayer != null) {
            val packetToSend = disableFlyAbilitiesPacket.clone()
            packetToSend.uniqueEntityId = session.localPlayer.uniqueEntityId
            session.clientBound(packetToSend) // Отправляем пакет способности КЛИЕНТУ
            clientFlightAbilitySent = false
            // Оригинальный код имел 'canFly = false' здесь
            // Оригинальный код имел 'return' здесь - удаляем, т.к. можем захотеть обработать сам пакет далее
        }


        // Перехватываем определенные пакеты независимо от направления (т.к. информация о направлении недоступна)
        // Полагаемся на тип пакета, чтобы определить направление, где это возможно (например, PlayerAuthInputPacket всегда C->S)

        // Перехватываем RequestAbilityPacket (обычно C->S или S->C)
        if (packet is RequestAbilityPacket && isEnabled) { // Перехватываем только когда модуль включен
             // Оригинальный код перехватывал все RequestAbilityPacket для FLYING
             // Оставляем это - вероятно, предназначено для скрытия запроса клиента или запроса сервера
             if (packet.ability == Ability.FLYING) { // Проверяем конкретную способность, как в оригинале
                interceptablePacket.intercept()
                return
             }
        }

        // Перехватываем UpdateAbilitiesPacket (обычно S->C)
        if (packet is UpdateAbilitiesPacket && isEnabled) { // Перехватываем только когда модуль включен
            // Оригинальный код перехватывал все UpdateAbilitiesPacket.
            // Это предотвращает сервер от изменения способностей клиента, включая MAY_FLY, который мы отправили.
            interceptablePacket.intercept()
            return
        }

        // Перехватываем PlayerAuthInputPacket (ВСЕГДА C->S)
        // Этот пакет идет от клиента к серверу.
        if (packet is PlayerAuthInputPacket) {
            // Если модуль включен, модифицируем пакет перед отправкой на сервер, чтобы скрыть индикаторы полета.
            if (isEnabled) {
                val inputData = packet.inputData

                // Удаляем флаги, которые явно указывают на клиентский полет или его стандартные контроли
                inputData.remove(PlayerAuthInputData.START_FLYING)
                inputData.remove(PlayerAuthInputData.STOP_FLYING)
                inputData.remove(PlayerAuthInputData.ASCEND) // Используется для вертикального движения
                inputData.remove(PlayerAuthInputData.DESCEND) // Используется для вертикального движения
                inputData.remove(PlayerAuthInputData.CHANGE_HEIGHT) // Используется для вертикального движения/плавания
                inputData.remove(PlayerAuthInputData.JUMPING) // Используется для подъема в клиентском полете
                inputData.remove(PlayerAuthInputData.SNEAKING) // Используется для спуска в клиентском полете

                // Оригинальный код имел логику SetEntityMotionPacket здесь - УДАЛЕНА
                // Клиент должен двигаться сам на основе способности MAY_FLY

                // packet.position и packet.delta отправляются без изменений, отражая реальное положение и движение клиента
                // Это полагается на простые античиты, проверяющие только флаги, а не сложную физику/историю позиций.
            }
            // Если модуль выключен, PlayerAuthInputPacket обрабатывается нормально.
        }

        // Примечание: В этой структуре невозможно надежно перехватывать пакеты MovePlayerPacket
        // или SetEntityMotionPacket, идущие ОТ сервера К клиенту для локального игрока,
        // так как направление пакета недоступно в InterceptablePacket.
        // Оригинальный код тоже не обрабатывал их для этой цели.
        // Это ограничение структуры фреймворка для продвинутого MITM-обхода.
    }
}
