package ru.sashil.bot.commands

enum class BotCommandType(
    val displayName: String,
    val description: String
) {
    REGISTER_PARENT(
        displayName = "Зарегистрироваться как родитель",
        description = "Регистрация родителя в системе"
    ),
    REGISTER_CHILD(
        displayName = "Зарегистрировать ребенка",
        description = "Регистрация нового ребенка для занятий"
    ),
    SELECT_GROUP(
        displayName = "Выбрать группу для занятий",
        description = "Подбор группы по возрасту и навыкам"
    ),
    UPLOAD_CERTIFICATE(
        displayName = "Прикрепить справку",
        description = "Загрузка медицинской справки"
    ),
    REPORT_ABSENCE(
        displayName = "Сообщить о пропуске занятия",
        description = "Уведомление о пропуске занятия"
    ),
    UPLOAD_RECEIPT(
        displayName = "Сообщить об оплате",
        description = "Загрузка квитанции об оплате"
    ),
    MESSAGE_ADMIN(
        displayName = "Написать администратору",
        description = "Связь с администрацией"
    ),
    MESSAGE_COACH(
        displayName = "Написать тренеру",
        description = "Связь с тренером"
    ),
    EDIT_PARENT(
        displayName = "Редактировать свои данные",
        description = "Изменение личных данных родителя"
    ),
    EDIT_CHILD(
        displayName = "Редактировать ребенка",
        description = "Изменение данных ребенка"
    ),
    HELP(
        displayName = "Помощь",
        description = "Проблемы при регистрации и выборе группы"
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