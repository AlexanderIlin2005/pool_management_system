package ru.sashil.bot.commands

class HelpCommand : BaseBotCommand() {
    override val displayName: String = "Проблемы при регистрации и выборе группы"
    override val description: String = "Помощь в решении проблем"

    override fun start(userId: Long): CommandResult {
        return CommandResult.Complete(getHelpMessage())
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        return CommandResult.Complete(getHelpMessage())
    }

    private fun getHelpMessage(): String {
        val adminNumber = BotCommandType.MESSAGE_ADMIN.getCommandNumber()
        val coachNumber = BotCommandType.MESSAGE_COACH.getCommandNumber()

        return """
            Если у Вас возникли проблемы при регистрации или выборе группы:

            • Проверьте, что Вы правильно ввели все данные
            • Убедитесь, что Ваш ребенок соответствует возрастным критериям
            • Свяжитесь с администратором через команду $adminNumber
            • Свяжитесь с тренером через команду $coachNumber

            Для возврата в главное меню напишите "отмена" или "нет".
        """.trimIndent()
    }
}
