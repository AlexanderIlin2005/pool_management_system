package ru.sashil.bot

import io.github.blackbaroness.vk.VkClient
import io.github.blackbaroness.vk.model.method.GetUpdatesVkMethod
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.*
import ru.sashil.bot.handlers.*
import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.CommandUtils
import ru.sashil.common.util.ConfigLoader
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class BotApplication {
    companion object {
        private val LOGGER = Logger.getLogger(BotApplication::class.java.name)
        private lateinit var dbService: DatabaseService
        private lateinit var regHandler: RegistrationHandler
        private lateinit var editHandler: ProfileEditHandler
        private lateinit var childHandler: ChildRegistrationHandler
        private lateinit var childEditHandler: ChildEditHandler
        private lateinit var notificationService: NotificationService

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                ConfigLoader.load()

                val dbUrl = "jdbc:postgresql://${ConfigLoader.get("DB_HOST")}:${ConfigLoader.get("DB_PORT")}/${ConfigLoader.get("DB_NAME")}"
                dbService = DatabaseService(dbUrl, ConfigLoader.get("DB_USER"), ConfigLoader.get("DB_PASSWORD"))

                regHandler = RegistrationHandler()
                editHandler = ProfileEditHandler()
                childHandler = ChildRegistrationHandler()
                childEditHandler = ChildEditHandler()

                val vkToken = ConfigLoader.get("VK_BOT_TOKEN")
                val groupId = 239874040L

                val bot = VkClient(
                    token = vkToken,
                    httpClient = HttpClient(CIO)
                )

                // Инициализация сервиса уведомлений
                notificationService = NotificationService(dbService, bot)

                LOGGER.info("Бот запущен!")

                runBlocking {
                    bot.groups.setLongPollSettings(groupId) {
                        enabled = true
                        messageNew = true
                    }

                    // Запускаем планировщик уведомлений в отдельной корутине
                    launch {
                        startNotificationScheduler(bot)
                    }

                    // Запуск polling
                    bot.startLongPolling(groupId, null).collect { update ->
                        processUpdate(bot, update)
                    }
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Критическая ошибка: ${e.message}", e)
            }
        }

        // ИСПРАВЛЕНИЕ: Делаем метод расширением для CoroutineScope, чтобы видеть isActive
        private suspend fun CoroutineScope.startNotificationScheduler(bot: VkClient) {
            // Проверяем уведомления каждый час
            while (isActive) {
                try {
                    notificationService.checkAndSendNotifications()
                } catch (e: Exception) {
                    LOGGER.severe("Ошибка в планировщике уведомлений: ${e.message}")
                }
                delay(60 * 60 * 1000) // 1 час
            }
        }

        private suspend fun processUpdate(bot: VkClient, update: GetUpdatesVkMethod.Result.Update) {
            val msgNew = update.asMessageNew ?: return
            val msg = msgNew.message
            val userId = msg.fromId
            val text = msg.text

            try {
                when {
                    regHandler.isRegistering(userId) -> handleRegistration(bot, userId, text)
                    editHandler.isEditing(userId) -> handleEditProfile(bot, userId, text)
                    childHandler.isAddingChild(userId) -> handleAddChild(bot, userId, text)
                    childEditHandler.isEditingChild(userId) -> handleEditChild(bot, userId, text)
                    else -> handleCommands(bot, userId, text)
                }
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Ошибка обработки сообщения от $userId: ${e.message}", e)
                bot.messages.send(userId) {
                    message = "Произошла внутренняя ошибка."
                    randomId = Random().nextInt(Int.MAX_VALUE)
                }
            }
        }

        private suspend fun handleRegistration(bot: VkClient, userId: Long, text: String) {
            val result = regHandler.processStep(userId, text)
            if (result == "SAVE_PARENT") {
                val data = regHandler.getData(userId)
                dbService.saveParent(userId, data["firstName"], data["lastName"], data["middleName"], data["email"])
                sendText(bot, userId, "Регистрация завершена.")
                regHandler.clearData(userId)
            } else {
                sendText(bot, userId, result)
            }
        }

        private suspend fun handleEditProfile(bot: VkClient, userId: Long, text: String) {
            val result = editHandler.processStep(userId, text, dbService)
            sendText(bot, userId, result)
        }

        private suspend fun handleAddChild(bot: VkClient, userId: Long, text: String) {
            try {
                val result = childHandler.processStep(userId, text, dbService)
                sendText(bot, userId, result)
            } catch (e: Exception) {
                sendText(bot, userId, "Ошибка при добавлении: ${e.message}")
                childHandler.cancel(userId)
            }
        }

        private suspend fun handleEditChild(bot: VkClient, userId: Long, text: String) {
            try {
                val result = childEditHandler.processStep(userId, text, dbService)
                sendText(bot, userId, result)
            } catch (e: Exception) {
                sendText(bot, userId, "Ошибка при обновлении: ${e.message}")
                childEditHandler.cancel(userId)
            }
        }

        private suspend fun handleCommands(bot: VkClient, userId: Long, text: String) {
            val cmd = CommandUtils.normalize(text)
            when (cmd) {
                "начать", "start" -> {
                    if (dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Вы уже зарегистрированы.")
                    } else {
                        sendText(bot, userId, "Добро пожаловать! Введите вашу фамилию:")
                        regHandler.startRegistration(userId)
                    }
                }
                "редактировать", "профиль" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        val data = dbService.getParentData(userId)
                        if (data != null) {
                            editHandler.startEditing(userId, data)
                            sendText(bot, userId, "Режим редактирования. Текущая фамилия: ${data["lastName"]}. Введите новую:")
                        }
                    }
                }
                "справка" -> sendText(bot, userId, "Пришлите фото справки одним сообщением.")
                "добавитьребенка" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        childHandler.startAddingChild(userId)
                        sendText(bot, userId, "Введите фамилию ребенка:")
                    }
                }
                "дети" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        val children = dbService.getChildrenByParentVkId(userId)
                        if (children.isEmpty()) {
                            sendText(bot, userId, "У вас пока нет детей.")
                        } else {
                            val sb = StringBuilder("Ваши дети:\n")
                            children.forEachIndexed { i, c ->
                                sb.append("${i + 1}. ${c["lastName"]} ${c["firstName"]} (${c["age"]} лет, ${c["gradeNumber"]} кл.)\n")
                            }
                            sb.append("\nНапишите номер для редактирования.")
                            sendText(bot, userId, sb.toString())
                        }
                    }
                }
                "уведомления" -> {
                    if (!dbService.isParentRegistered(userId)) {
                        sendText(bot, userId, "Сначала зарегистрируйтесь.")
                    } else {
                        val isEnabled = dbService.isRegularNotificationsEnabled(userId)
                        val status = if (isEnabled) "включены" else "выключены"
                        sendText(bot, userId, "Регулярные напоминания о занятиях сейчас $status.\nНапишите 'включить уведомления' или 'выключить уведомления', чтобы изменить.")
                    }
                }
                "включитьуведомления" -> {
                    dbService.toggleRegularNotifications(userId, true)
                    sendText(bot, userId, "Регулярные напоминания включены.")
                }
                "выключитьуведомления" -> {
                    dbService.toggleRegularNotifications(userId, false)
                    sendText(bot, userId, "Регулярные напоминания выключены. Вы все равно будете получать уведомления об отменах занятий.")
                }
                "договор", "согласие", "правила", "квитанция" -> sendText(bot, userId, "Отправка документов временно отключена.")
                else -> {
                    if (text.matches("\\d+".toRegex())) {
                        val num = text.toInt()
                        val children = dbService.getChildrenByParentVkId(userId)
                        if (num > 0 && num <= children.size) {
                            val child = children[num - 1]
                            childEditHandler.startEditingChild(userId, (child["id"] as Long), child)
                            sendText(bot, userId, "Редактирование: ${child["lastName"]}. Введите новую фамилию:")
                        }
                    }
                }
            }
        }

        private suspend fun sendText(bot: VkClient, userId: Long, text: String) {
            bot.messages.send(userId) {
                message = text
                randomId = Random().nextInt(Int.MAX_VALUE)
            }
        }
    }
}