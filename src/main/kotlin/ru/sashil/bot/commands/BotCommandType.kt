package ru.sashil.bot.commands

enum class BotCommandType(
    val displayName: String,
    val description: String
) {
    REGISTER(
        displayName = "Зарегистрировать ребенка в бассейн",
        description = "Регистрация нового ребенка для занятий"
    ),
    SELECT_GROUP(
        displayName = "Выбрать группу для занятий в бассейне",
        description = "Подбор группы по возрасту и навыкам"
    ),
    UPLOAD_CERTIFICATE(
        displayName = "Прикрепить справку о допуске в бассейн",
        description = "Загрузка медицинской справки"
    ),
    REPORT_ABSENCE(
        displayName = "Сообщить о пропуске занятия тренеру",
        description = "Уведомление о пропуске занятия"
    ),
    UPLOAD_RECEIPT(
        displayName = "Сообщить об оплате абонемента",
        description = "Загрузка квитанции об оплате"
    ),
    MESSAGE_ADMIN(
        displayName = "Написать сообщение администратору",
        description = "Связь с администрацией"
    ),
    MESSAGE_COACH(
        displayName = "Написать сообщение тренеру",
        description = "Связь с тренером"
    ),
    HELP(
        displayName = "Проблемы при регистрации и выборе группы",
        description = "Помощь в решении проблем"
    );

    companion object {
        fun fromNumber(number: Int): BotCommandType? {
            return values().getOrNull(number - 1)
        }

        fun getCommandsList(): String {
            return values().joinToString("\n") {
                "${it.ordinal + 1}. ${it.displayName}"
            }
        }
    }
}
