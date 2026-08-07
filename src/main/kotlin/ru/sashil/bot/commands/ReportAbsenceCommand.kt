package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import ru.sashil.common.util.CommandUtils
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class ReportAbsenceCommand(
    private val dbService: DatabaseService,
    private val minioService: MinIOService
) : BotCommand {

    override val displayName: String = "Сообщить о пропуске занятия тренеру"
    override val description: String = "Уведомление о пропуске занятия"

    private val logger = Logger.getLogger(ReportAbsenceCommand::class.java.name)
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
            userSteps.remove(userId)
            userData.remove(userId)
            return CommandResult.Cancel()
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
                        "У Вас пока нет зарегистрированных детей. Сначала зарегистрируйте ребенка (команда ${BotCommandType.REGISTER_CHILD.getCommandNumber()})."
                    )
                }

                if (children.size == 1) {
                    val child = children[0]
                    data["childId"] = (child["id"] as Number).toLong()
                    data["childName"] = "${child["lastName"]} ${child["firstName"]}"
                    userSteps[userId] = 3
                    return showAbsenceTypes()
                } else {
                    userSteps[userId] = 2
                    val sb = StringBuilder("Выберите ребенка, о пропуске которого Вы хотите сообщить:\n\n")
                    children.forEachIndexed { i, child ->
                        val name = "${child["lastName"]} ${child["firstName"]}"
                        val middleName = child["middleName"] as? String
                        if (!middleName.isNullOrEmpty()) {
                            sb.append("${i + 1}. $name $middleName\n")
                        } else {
                            sb.append("${i + 1}. $name\n")
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
                    return CommandResult.Continue("Пожалуйста, введите номер ребенка от 1 до ${children.size}.")
                }
                val child = children[num - 1]
                data["childId"] = (child["id"] as Number).toLong()
                data["childName"] = "${child["lastName"]} ${child["firstName"]}"
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

                when (type) {
                    "SICK" -> {
                        userSteps[userId] = 4
                        return CommandResult.Continue("Пожалуйста, пришлите справку от врача (фото или PDF файл).")
                    }
                    "UNWELL", "OTHER" -> {
                        userSteps[userId] = 5
                        return CommandResult.Continue(
                            "Укажите дату пропуска в формате ДД.ММ (например, 15.08):\n" +
                                    "(По умолчанию будет использован текущий год)"
                        )
                    }
                    else -> return saveAbsence(userId, data)
                }
            }
            4 -> {
                // Загрузка справки для SICK
                if (rawJson == null || !rawJson.contains("\"attachments\"")) {
                    return CommandResult.Continue("Я не вижу вложения. Пожалуйста, пришлите справку от врача (фото или PDF).")
                }

                try {
                    val fileInfo = getFileUrlAndExt(rawJson)
                    if (fileInfo == null) {
                        return CommandResult.Continue("Не удалось найти файл. Попробуйте снова.")
                    }

                    val fileUrl = fileInfo[0]
                    val extension = fileInfo[1]

                    val file = downloadFile(fileUrl)
                    if (file == null) {
                        return CommandResult.Continue("Ошибка скачивания файла. Попробуйте снова.")
                    }

                    val objectName = "certificates/${java.util.UUID.randomUUID()}$extension"
                    val url = minioService.uploadFile(file.absolutePath, "certificate$extension")
                    file.delete()

                    data["certificateUrl"] = url
                    data["certificateFileName"] = "справка_о_болезни$extension"

                    return saveAbsence(userId, data)

                } catch (e: Exception) {
                    logger.severe("Ошибка загрузки справки: ${e.message}")
                    return CommandResult.Error("Ошибка загрузки справки: ${e.message}")
                }
            }
            5 -> {
                // Ввод даты для UNWELL / OTHER
                val dateStr = text.trim()
                val parsedDate = parseShortDate(dateStr)
                if (parsedDate == null) {
                    return CommandResult.Continue(
                        "❌ Неверный формат даты. Используйте ДД.ММ (например, 15.08)."
                    )
                }
                data["absenceDate"] = parsedDate.toString() // ISO format yyyy-MM-dd
                return saveAbsence(userId, data)
            }
            else -> {
                userSteps.remove(userId)
                userData.remove(userId)
                return CommandResult.Cancel()
            }
        }
    }

    /**
     * Парсит дату в формате ДД.ММ, добавляя текущий год.
     * Возвращает LocalDate или null при ошибке.
     */
    private fun parseShortDate(text: String): LocalDate? {
        try {
            val parts = text.split(".")
            if (parts.size != 2) return null
            val day = parts[0].trim().toInt()
            val month = parts[1].trim().toInt()
            val year = LocalDate.now().year
            val date = LocalDate.of(year, month, day)
            // Проверка разумности: не более 6 месяцев в будущее и не более 1 месяца в прошлое
            val now = LocalDate.now()
            if (date.isAfter(now.plusMonths(6)) || date.isBefore(now.minusMonths(1))) {
                return null
            }
            return date
        } catch (_: Exception) {
            return null
        }
    }

    private fun saveAbsence(userId: Long, data: MutableMap<String, Any>): CommandResult {
        try {
            val childId = data["childId"] as Long
            val childName = data["childName"] as String
            val type = data["absenceType"] as String
            val parentId = dbService.getParentIdByVkId(userId) ?: return CommandResult.Error("Родитель не найден")

            val absenceTypeDisplay = when (type) {
                "SICK" -> "Болезнь (со справкой)"
                "UNWELL" -> "Недомогание (без справки)"
                else -> "Другая причина"
            }

            var message = "Ребенок $childName пропустит занятие по причине: $absenceTypeDisplay"

            val certificateUrl = data["certificateUrl"] as? String
            if (certificateUrl != null) {
                message += "\nСправка приложена"
            }

            val absenceDate = data["absenceDate"] as? String
            if (absenceDate != null) {
                val formattedDate = try {
                    LocalDate.parse(absenceDate).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                } catch (_: Exception) { absenceDate }
                message += "\nДата пропуска: $formattedDate"
            }

            val sql = """
                INSERT INTO pool.absence_notifications
                (parent_id, child_id, absence_type, message, status, created_at, updated_at, certificate_url, certificate_file_name, absence_date)
                VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?)
            """.trimIndent()

            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, parentId)
                    stmt.setLong(2, childId)
                    stmt.setString(3, type)
                    stmt.setString(4, message)
                    stmt.setString(5, certificateUrl)
                    stmt.setString(6, data["certificateFileName"] as? String)
                    if (absenceDate != null) {
                        stmt.setDate(7, java.sql.Date.valueOf(LocalDate.parse(absenceDate)))
                    } else {
                        stmt.setNull(7, java.sql.Types.DATE)
                    }
                    stmt.executeUpdate()
                }
            }

            userSteps.remove(userId)
            userData.remove(userId)

            return when (type) {
                "SICK" -> CommandResult.Complete(
                    "Сообщение о пропуске передано администратору и тренеру.\n\n" +
                            "Желаем Вашему ребенку скорейшего выздоровления! Справка от врача приложена.\n" +
                            "Занятие можно будет отработать по согласованию с администратором."
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

    private fun showAbsenceTypes(): CommandResult {
        return CommandResult.Continue(
            "Пожалуйста, выберите пункт и напишите цифру:\n" +
                    "1. Ребенок пропустит занятие по причине болезни (справка от врача)\n" +
                    "2. Ребенок пропустит занятие по причине недомогания (без справки от врача)\n" +
                    "3. Ребенок пропустит занятия по другой причине"
        )
    }

    private fun getFileUrlAndExt(jsonString: String): Array<String>? {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("message")) return null
            val msgObj = root.getJSONObject("message")
            if (!msgObj.has("attachments")) return null

            val attachments = msgObj.getJSONArray("attachments")
            for (i in 0 until attachments.length()) {
                val attachment = attachments.getJSONObject(i)
                val type = attachment.getString("type")

                if ("photo" == type) {
                    val photoObj = attachment.getJSONObject("photo")
                    val sizes = photoObj.getJSONArray("sizes")
                    if (sizes.length() > 0) {
                        val url = sizes.getJSONObject(sizes.length() - 1).getString("url")
                        return arrayOf(url, ".jpg")
                    }
                } else if ("doc" == type) {
                    val docObj = attachment.getJSONObject("doc")
                    if (docObj.has("url")) {
                        val url = docObj.getString("url")
                        var ext = docObj.optString("ext", "")
                        if (!ext.startsWith(".") && ext.isNotEmpty()) {
                            ext = ".$ext"
                        }
                        if (ext.isEmpty()) ext = ".jpg"
                        return arrayOf(url, ext)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("Ошибка получения URL файла: ${e.message}")
        }
        return null
    }

    private fun downloadFile(urlString: String): File? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val inputStream = connection.inputStream
            val tempFile = File.createTempFile("cert_", ".tmp")
            FileOutputStream(tempFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            inputStream.close()
            connection.disconnect()
            return tempFile
        } catch (e: Exception) {
            logger.severe("Ошибка скачивания: ${e.message}")
            return null
        }
    }

    override fun cancel(userId: Long): CommandResult {
        userSteps.remove(userId)
        userData.remove(userId)
        return CommandResult.Cancel()
    }
}