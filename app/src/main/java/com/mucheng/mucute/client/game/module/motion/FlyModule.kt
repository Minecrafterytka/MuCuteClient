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
// Добавляем пакеты, которые будем перехватывать от сервера, даже без явного направления
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket


class FlyModule : Module("fly", ModuleCategory.Motion) {

    private var flySpeed by floatValue("flySpeed", 0.15f, 0.1f..1.5f)

    // Пакеты для включения/выключения полета на стороне клиента (чтобы работало управление)
    // Отправляем их клиенту, чтобы кнопки прыжка/красться использовались как ввод для вертикали
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
            // flySpeed будет установлен динамически при отправке
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
    private var clientFlightAbilitySent = false

    // ** Важно для Packet Flight **
    // Позиция игрока, которую мы *последний раз отправили* серверу.
    // Используется для расчета дельты в следующем PlayerAuthInputPacket.
    // Инициализируется при включении модуля на основе позиции клиента.
    private var lastPositionSentToServer: Vector3f? = null


    // onEnable/onDisable отсутствуют в этой структуре, используем beforePacketBound для управления состоянием

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // ** Логика управления состоянием модуля и отправки способностей клиенту **
        // Эта часть адаптирована к структуре без onEnable/onDisable
        if (isEnabled && !clientFlightAbilitySent && session?.localPlayer != null) {
             // Модуль включен, и мы еще не отправили клиенту способность летать
             val packetToSend = enableFlyAbilitiesPacket.clone()
             packetToSend.uniqueEntityId = session.localPlayer.uniqueEntityId
             // Устанавливаем скорость полета динамически из настройки модуля
             packetToSend.abilityLayers.find { it.layerType == AbilityLayer.Type.BASE }?.flySpeed = this.flySpeed
             session.clientBound(packetToSend) // Отправляем пакет способности КЛИЕНТУ
             clientFlightAbilitySent = true
             // При первом включении сбрасываем отслеживаемую позицию.
             // Она будет инициализирована первым же пакетом PlayerAuthInputPacket.
             lastPositionSentToServer = null
        } else if (!isEnabled && clientFlightAbilitySent && session?.localPlayer != null) {
            // Модуль выключен, но способность летать клиенту отправлена. Отправляем пакет на отключение.
            val packetToSend = disableFlyAbabilitiesPacket.clone()
            packetToSend.uniqueEntityId = session.localPlayer.uniqueEntityId
            session.clientBound(packetToSend) // Отправляем пакет способности КЛИЕНТУ
            clientFlightAbilitySent = false
            // При выключении сбрасываем отслеживаемую позицию
            lastPositionSentToServer = null
        }


        // ** Перехватываем определенные пакеты независимо от направления **
        // (т.к. информация о направлении недоступна в этой структуре)
        // Полагаемся на тип пакета и контекст (наш ли игрок), чтобы принять решение.

        // Перехватываем RequestAbilityPacket (обычно C->S или S->C)
        if (packet is RequestAbilityPacket && isEnabled) { // Перехватываем только когда модуль включен
             // Оригинальный код перехватывал все RequestAbilityPacket для FLYING
             // Это, вероятно, нужно для скрытия запроса клиента или запроса сервера о состоянии FLYING.
             if (packet.ability == Ability.FLYING) { // Проверяем конкретную способность
                interceptablePacket.intercept() // Предотвращаем попадание пакета в пункт назначения
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

        // Перехватываем MovePlayerPacket (обычно S->C коррекции/телепорты, C->S в очень старых версиях)
        // Если пакет для нашего игрока и модуль включен, перехватываем.
        // Это попытка предотвратить серверные коррекции позиции, которые могут конфликтовать с packet flight.
        if (packet is MovePlayerPacket && isEnabled && session?.localPlayer != null && packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
             // Без информации о направлении, мы не знаем точно, это коррекция сервера или старый пакет клиента.
             // Предполагаем, что для современных версий это почти всегда S->C коррекция.
             interceptablePacket.intercept() // Перехватываем, чтобы сервер не менял нашу позицию
             return
        }

         // Перехватываем SetEntityMotionPacket (обычно S->C)
         // Если пакет для нашего игрока и модуль включен, перехватываем.
         // Предотвращает сервер от принудительной установки скорости нашего игрока.
         if (packet is SetEntityMotionPacket && isEnabled && session?.localPlayer != null && packet.runtimeEntityId == session.localPlayer.runtimeEntityId) {
             interceptablePacket.intercept()
             return
         }


        // ** Перехватываем PlayerAuthInputPacket (ВСЕГДА C->S) **
        // Этот пакет от клиента к серверу. Мы будем его ИЗМЕНЯТЬ.
        if (packet is PlayerAuthInputPacket) {
            // Если модуль включен, манипулируем этим пакетом для симуляции полета
            if (isEnabled) {
                // Инициализируем lastPositionSentToServer при самом первом PAI пакете после включения
                if (lastPositionSentToServer == null) {
                    // Берем текущую позицию клиента как стартовую точку для нашего packet flight
                    lastPositionSentToServer = packet.position
                }

                val inputData = packet.inputData

                // ** Расчет вертикального движения на основе ввода клиента **
                // Мы используем флаги JUMPING/SNEAKING как ввод для вертикали, т.к. клиент сам не делает это при MAY_FLY
                var verticalMovementDelta = 0f
                if (inputData.contains(PlayerAuthInputData.JUMPING)) {
                    verticalMovementDelta += flySpeed
                }
                if (inputData.contains(PlayerAuthInputData.SNEAKING)) {
                    verticalMovementDelta -= flySpeed
                }

                // ** Расчет горизонтального движения **
                // Используем дельту клиента для горизонтальных координат. Предполагаем, что клиент корректно рассчитывает горизонтальное движение.
                val horizontalDelta = Vector3f.from(packet.delta.x, 0f, packet.delta.z)

                // ** Расчет НОВОЙ позиции и дельты для отправки на сервер **
                // Новая позиция = Последняя позиция, которую мы ОТПРАВИЛИ + Горизонтальная дельта клиента + Наша вертикальная дельта
                val newPosition = lastPositionSentToServer!! // lastPositionSentToServer уже инициализирован
                    .add(horizontalDelta) // Добавляем горизонтальное движение клиента
                    .add(0f, verticalMovementDelta, 0f) // Добавляем наше вертикальное движение

                // Новая дельта = Новая позиция - Последняя позиция, которую мы ОТПРАВИЛИ
                val newDelta = newPosition.sub(lastPositionSentToServer!!)

                // ** ОБНОВЛЯЕМ пакет **
                packet.position = newPosition // Заменяем клиентскую позицию на нашу рассчитанную
                packet.delta = newDelta     // Заменяем клиентскую дельту на нашу рассчитанную

                // ** ОБНОВЛЯЕМ нашу отслеживаемую позицию для следующего тика **
                lastPositionSentToServer = newPosition

                // ** Удаляем флаги, которые явно выдают клиентский полет или его стандартные контроли **
                // Мы удаляем их, потому что МЫ сами контролируем позицию, а не клиентская логика полета.
                inputData.remove(PlayerAuthInputData.START_FLYING) // Клиент сообщает, что начал лететь
                inputData.remove(PlayerAuthInputData.STOP_FLYING) // Клиент сообщает, что перестал лететь
                inputData.remove(PlayerAuthInputData.ASCEND) // Используется для вертикального движения
                inputData.remove(PlayerAuthInputData.DESCEND) // Используется для вертикального движения
                inputData.remove(PlayerAuthInputData.CHANGE_HEIGHT) // Используется для вертикального движения/плавания
                inputData.remove(PlayerAuthInputData.JUMPING) // Удаляем, т.к. мы использовали этот флаг для расчета подъема
                inputData.remove(PlayerAuthInputData.SNEAKING) // Удаляем, т.к. мы использовали этот флаг для расчета спуска

                // Модифицированный пакет продолжит свой путь к серверу с нашей фейковой позицией/дельтой
            }
            // Если модуль выключен, PlayerAuthInputPacket обрабатывается нормально.
        }
    }
}
