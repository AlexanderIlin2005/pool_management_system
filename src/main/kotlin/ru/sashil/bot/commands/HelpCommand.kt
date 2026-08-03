package ru.sashil.bot.commands

class HelpCommand : BotCommand {
    override val displayName: String = "Проблемы при регистрации и выборе группы"
    override val description: String = "Помощь в решении проблем"

    override fun start(userId: Long): CommandResult {
        return CommandResult.Complete(getHelpMessage())
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        return CommandResult.Complete(getHelpMessage())
    }

    private fun getHelpMessage(): String {
        return """
            Если у Вас возникли проблемы при регистрации или выборе группы:

            • Проверьте, что Вы правильно ввели все данные
            • Убедитесь, что Ваш ребенок соответствует возрастным критериям
            • Свяжитесь с администратором через команду 6
            • Свяжитесь с тренером через команду 7

            Для возврата в главное меню напишите "отмена" или "нет".
        """.trimIndent()
    }
}
