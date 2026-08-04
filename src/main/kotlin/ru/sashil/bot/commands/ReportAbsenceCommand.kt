package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import ru.sashil.common.util.CommandUtils
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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

                // Если тип SICK - запрашиваем справку
                if (type == "SICK") {
                    userSteps[userId] = 4
                    return CommandResult.Continue(
                        "Пожалуйста, пришлите справку от врача (фото или PDF файл)."
                    )
                } else {
                    // Для UNWELL и OTHER - сразу сохраняем без справки
                    return saveAbsence(userId, data)
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

                    // Сохраняем справку в MinIO
                    val objectName = "certificates/${java.util.UUID.randomUUID()}$extension"
                    val url = minioService.uploadFile(file.absolutePath, "certificate$extension")
                    file.delete()

                    // Сохраняем URL справки в data
                    data["certificateUrl"] = url
                    data["certificateFileName"] = "справка_о_болезни$extension"

                    // Сохраняем уведомление о пропуске со справкой
                    return saveAbsence(userId, data)

                } catch (e: Exception) {
                    logger.severe("Ошибка загрузки справки: ${e.message}")
                    return CommandResult.Error("Ошибка загрузки справки: ${e.message}")
                }
            }
            else -> {
                return CommandResult.Cancel()
            }
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

            // Если есть справка - добавляем информацию о ней
            val certificateUrl = data["certificateUrl"] as? String
            if (certificateUrl != null) {
                message += "\nСправка приложена"
            }

            // Сохраняем в absence_notifications
            val sql = """
                INSERT INTO pool.absence_notifications
                (parent_id, child_id, absence_type, message, status, created_at, updated_at, certificate_url, certificate_file_name)
                VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)
            """.trimIndent()

            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, parentId)
                    stmt.setLong(2, childId)
                    stmt.setString(3, type)
                    stmt.setString(4, message)
                    stmt.setString(5, certificateUrl)
                    stmt.setString(6, data["certificateFileName"] as? String)
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