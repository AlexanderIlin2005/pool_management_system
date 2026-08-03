package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService

/**
 * Базовый интерфейс для всех команд бота
 */
interface BotCommand {
    val displayName: String
    val description: String

    fun start(userId: Long): CommandResult
    fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult
    fun cancel(userId: Long): CommandResult = CommandResult.Cancel()
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
    private val commandMap = mutableMapOf<BotCommandType, () -> BotCommand>()

    fun registerCommand(type: BotCommandType, factory: () -> BotCommand) {
        commandMap[type] = factory
    }

    fun createCommand(type: BotCommandType, dbService: DatabaseService, minioService: MinIOService? = null): BotCommand {
        return when (type) {
            BotCommandType.REGISTER -> RegisterCommand(dbService)
            BotCommandType.SELECT_GROUP -> SelectGroupCommand(dbService)
            BotCommandType.UPLOAD_CERTIFICATE -> UploadCertificateCommand(dbService, minioService!!)
            BotCommandType.REPORT_ABSENCE -> ReportAbsenceCommand(dbService)
            BotCommandType.UPLOAD_RECEIPT -> UploadReceiptCommand(dbService, minioService!!)
            BotCommandType.MESSAGE_ADMIN -> MessageAdminCommand(dbService)
            BotCommandType.MESSAGE_COACH -> MessageCoachCommand(dbService)
            BotCommandType.HELP -> HelpCommand()
        }
    }
}
