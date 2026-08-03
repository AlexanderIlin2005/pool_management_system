package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.util.CommandUtils
import java.util.concurrent.ConcurrentHashMap

class ReportAbsenceCommand(
    private val dbService: DatabaseService
) : BotCommand {

    override val displayName: String = "Сообщить о пропуске занятия тренеру"
    override val description: String = "Уведомление о пропуске занятия"

    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, Any>>()

    override fun start(userId: Long): CommandResult {
        userSteps[userId] = 1
        userData[userId] = mutableMapOf()
        return CommandResult.Continue(
            "Вы хотите проинформировать тренера о пропуске занятия в бассейне?\n\n(Да/Нет)"
        )
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = userSteps[userId] ?: return CommandResult.Error("Сессия не найдена")
        val data = userData[userId] ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (cmd == "нет" || cmd == "отмена") {
            if (step == 1) {
                return CommandResult.Cancel()
            } else {
                return CommandResult.Cancel()
            }
        }

        when (step) {
            1 -> {
                if (cmd != "да") {
                    return CommandResult.Continue(
                        "Для возврата в главное меню напишите 'нет'.\n\n" +
                        "Вы хотите проинформировать тренера о пропуске занятия в бассейне?"
                    )
                }

                val children = try {
                    dbService.getChildrenByParentVkId(userId)
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка получения данных: ${e.message}")
                }

                if (children.isEmpty()) {
                    return CommandResult.Continue(
                        "У Вас пока нет зарегистрированных детей. Сначала зарегистрируйте ребенка (команда 1)."
                    )
                }

                if (children.size == 1) {
                    val child = children[0]
                    data["childId"] = (child["id"] as Number).toLong()
                    data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
                    userSteps[userId] = 3
                    return showAbsenceTypes()
                } else {
                    userSteps[userId] = 2
                    val sb = StringBuilder("Выберите ребенка, о пропуске которого Вы хотите сообщить:\n\n")
                    children.forEachIndexed { i, child ->
                        val name = (child["lastName"] as String) + " " + (child["firstName"] as String)
                        if (child["middleName"] != null && (child["middleName"] as String).isNotEmpty()) {
                            sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]} ${child["middleName"]}\n")
                        } else {
                            sb.append("${i + 1}. ${child["lastName"]} ${child["firstName"]}\n")
                        }
                    }
                    return CommandResult.Continue(sb.toString())
                }
            }
            2 -> {
                val children = try {
                    dbService.getChildrenByParentVkId(userId)
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка получения данных: ${e.message}")
                }

                val num = text.trim().toIntOrNull()
                if (num == null || num < 1 || num > children.size) {
                    return CommandResult.Continue(
                        "Пожалуйста, введите номер ребенка от 1 до ${children.size}."
                    )
                }
                val child = children[num - 1]
                data["childId"] = (child["id"] as Number).toLong()
                data["childName"] = (child["lastName"] as String) + " " + (child["firstName"] as String)
                userSteps[userId] = 3
                return showAbsenceTypes()
            }
            3 -> {
                val type = when (text.trim()) {
                    "1" -> "SICK"
                    "2" -> "UNWELL"
                    "3" -> "OTHER"
                    else -> {
                        return CommandResult.Continue(
                            "Пожалуйста, выберите пункт и напишите цифру:\n" +
                            "1. Ребенок пропустит занятие по причине болезни (справка от врача)\n" +
                            "2. Ребенок пропустит занятие по причине недомогания (без справки от врача)\n" +
                            "3. Ребенок пропустит занятия по другой причине"
                        )
                    }
                }
                data["absenceType"] = type

                try {
                    val childId = data["childId"] as Long
                    val childName = data["childName"] as String
                    val parentId = dbService.getParentIdByVkId(userId) ?: return CommandResult.Error("Родитель не найден")

                    val absenceTypeDisplay = when (type) {
                        "SICK" -> "Болезнь (со справкой)"
                        "UNWELL" -> "Недомогание (без справки)"
                        else -> "Другая причина"
                    }

                    val message = "Ребенок $childName пропустит занятие по причине: $absenceTypeDisplay"

                    val sql = """
                        INSERT INTO pool.absence_notifications
                        (parent_id, child_id, absence_type, message, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.trimIndent()

                    dbService.getConnection().use { conn ->
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setLong(1, parentId)
                            stmt.setLong(2, childId)
                            stmt.setString(3, type)
                            stmt.setString(4, message)
                            stmt.executeUpdate()
                        }
                    }

                    userSteps.remove(userId)
                    userData.remove(userId)

                    return when (type) {
                        "SICK" -> CommandResult.Complete(
                            "Сообщение о пропуске передано администратору и тренеру.\n\n" +
                            "Желаем Вашему ребенку скорейшего выздоровления! Пожалуйста, прикрепите справку от врача для компенсации пропущенного занятия (занятие можно отработать по согласованию с администратором)."
                        )
                        "UNWELL" -> CommandResult.Complete(
                            "Сообщение о пропуске передано администратору и тренеру.\n\n" +
                            "Желаем Вашему ребенку скорейшего выздоровления! Вы можете согласовать с администратором возможность отработки занятия."
                        )
                        else -> CommandResult.Complete(
                            "Сообщение о пропуске передано администратору и тренеру."
                        )
                    }
                } catch (e: Exception) {
                    return CommandResult.Error("Ошибка сохранения уведомления о пропуске: ${e.message}")
                }
            }
            else -> {
                return CommandResult.Cancel()
            }
        }
    }

    private fun showAbsenceTypes(): CommandResult {
        return CommandResult.Continue(
            "Пожалуйста, выберите пункт и напишите цифру:\n" +
            "1. Ребенок пропустит занятие по причине болезни (справка от врача)\n" +
            "2. Ребенок пропустит занятие по причине недомогания (без справки от врача)\n" +
            "3. Ребенок пропустит занятия по другой причине"
        )
    }
}
