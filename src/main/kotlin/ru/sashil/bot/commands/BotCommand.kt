package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import java.util.concurrent.ConcurrentHashMap

/**
 * Базовый интерфейс для всех команд бота
 */
interface BotCommand {
    val displayName: String
    val description: String

    fun start(userId: Long): CommandResult
    fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult
    fun cancel(userId: Long): CommandResult = CommandResult.Cancel()

    // Методы для работы с сессией
    fun getStep(userId: Long): Int
    fun setStep(userId: Long, step: Int)
    fun getData(userId: Long): MutableMap<String, Any>?
    fun createData(userId: Long): MutableMap<String, Any>
    fun removeSession(userId: Long)
    fun hasSession(userId: Long): Boolean
}

/**
 * Абстрактный класс для всех команд с поддержкой сессий
 */
abstract class BaseBotCommand : BotCommand {
    protected val userSteps = ConcurrentHashMap<Long, Int>()
    protected val userData = ConcurrentHashMap<Long, MutableMap<String, Any>>()

    override fun getStep(userId: Long): Int = userSteps[userId] ?: 1
    override fun setStep(userId: Long, step: Int) { userSteps[userId] = step }
    override fun getData(userId: Long): MutableMap<String, Any>? = userData[userId]
    override fun createData(userId: Long): MutableMap<String, Any> {
        val data = mutableMapOf<String, Any>()
        userData[userId] = data
        return data
    }
    override fun removeSession(userId: Long) {
        userSteps.remove(userId)
        userData.remove(userId)
    }
    override fun hasSession(userId: Long): Boolean = userSteps.containsKey(userId)
}

/**
 * Результат выполнения команды
 */
sealed class CommandResult {
    data class Continue(val message: String) : CommandResult()
    data class Complete(val message: String) : CommandResult()
    data class Cancel(val message: String = "Действие отменено.") : CommandResult()
    data class Error(val message: String) : CommandResult()
}

/**
 * Фабрика команд - создает экземпляры команд по типу
 */
object CommandFactory {
    fun createCommand(type: BotCommandType, dbService: DatabaseService, minioService: MinIOService? = null): BotCommand {
        return when (type) {
            BotCommandType.REGISTER_PARENT -> RegisterParentCommand(dbService)
            BotCommandType.REGISTER_CHILD -> RegisterCommand(dbService)
            BotCommandType.SELECT_GROUP -> SelectGroupCommand(dbService)
            BotCommandType.UPLOAD_CERTIFICATE -> UploadCertificateCommand(dbService, minioService!!)
            BotCommandType.REPORT_ABSENCE -> ReportAbsenceCommand(dbService, minioService!!)
            BotCommandType.UPLOAD_RECEIPT -> UploadReceiptCommand(dbService, minioService!!)
            BotCommandType.MESSAGE_ADMIN -> MessageAdminCommand(dbService)
            BotCommandType.MESSAGE_COACH -> MessageCoachCommand(dbService)
            BotCommandType.EDIT_PARENT -> EditParentCommand(dbService)
            BotCommandType.EDIT_CHILD -> EditChildCommand(dbService)
            BotCommandType.HELP -> HelpCommand()
        }
    }
}
