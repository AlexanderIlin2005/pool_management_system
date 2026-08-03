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
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class UploadReceiptCommand(
    private val dbService: DatabaseService,
    private val minioService: MinIOService
) : BotCommand {

    override val displayName: String = "Сообщить об оплате абонемента"
    override val description: String = "Загрузка квитанции об оплате"

    private val logger = Logger.getLogger(UploadReceiptCommand::class.java.name)
    private val userSteps = ConcurrentHashMap<Long, Int>()
    private val userData = ConcurrentHashMap<Long, MutableMap<String, Any>>()

    override fun start(userId: Long): CommandResult {
        userSteps[userId] = 1
        userData[userId] = mutableMapOf()
        return CommandResult.Continue(
            "Вы хотите сообщить об оплате абонемента?\n\n(Да/Нет)"
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
                        "Вы хотите сообщить об оплате абонемента?"
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
                    userSteps[userId] = 3
                    return CommandResult.Continue(
                        "За какой месяц оплата?\n\nВведите в формате: ММ.ГГГГ (например, 09.2026)"
                    )
                } else {
                    userSteps[userId] = 2
                    val sb = StringBuilder("Выберите ребенка, для которого Вы хотите сообщить об оплате:\n\n")
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
                userSteps[userId] = 3
                return CommandResult.Continue(
                    "За какой месяц оплата?\n\nВведите в формате: ММ.ГГГГ (например, 09.2026)"
                )
            }
            3 -> {
                try {
                    val parts = text.trim().split("\\.".toRegex())
                    if (parts.size != 2) {
                        return CommandResult.Continue(
                            "Неверный формат. Используйте ММ.ГГГГ (например, 09.2026)"
                        )
                    }
                    val month = parts[0].toInt()
                    val year = parts[1].toInt()
                    if (month !in 1..12) {
                        return CommandResult.Continue("Месяц должен быть от 1 до 12.")
                    }
                    val monthYear = LocalDate.of(year, month, 1)

                    val now = LocalDate.now()
                    if (monthYear.isAfter(now.plusMonths(6))) {
                        return CommandResult.Continue("Нельзя оплачивать более чем за 6 месяцев вперед.")
                    }

                    data["monthYear"] = monthYear
                    userSteps[userId] = 4
                    return CommandResult.Continue(
                        "Пожалуйста, пришлите фото квитанции (изображение или PDF файл)."
                    )
                } catch (e: Exception) {
                    return CommandResult.Continue(
                        "Неверный формат. Используйте ММ.ГГГГ (например, 09.2026)"
                    )
                }
            }
            4 -> {
                if (rawJson == null || !rawJson.contains("\"attachments\"")) {
                    return CommandResult.Continue("Я не вижу вложения. Пришлите файл с квитанцией.")
                }

                try {
                    val fileInfo = getFileUrlAndExt(rawJson)
                    if (fileInfo == null) {
                        return CommandResult.Continue("Не удалось найти файл. Попробуйте снова.")
                    }

                    val fileUrl = fileInfo[0]
                    val extension = fileInfo[1]
                    val originalName = if (fileInfo.size > 2) fileInfo[2] else "квитанция$extension"

                    val file = downloadFile(fileUrl)
                    if (file == null) {
                        return CommandResult.Continue("Ошибка скачивания файла. Попробуйте снова.")
                    }

                    val objectName = "receipts/${java.util.UUID.randomUUID()}$extension"
                    val url = minioService.uploadFile(file.absolutePath, "receipt$extension")
                    file.delete()

                    val childId = data["childId"] as Long
                    val monthYear = data["monthYear"] as LocalDate

                    dbService.savePaymentReceipt(userId, childId, monthYear, url, originalName)

                    userSteps.remove(userId)
                    userData.remove(userId)

                    return CommandResult.Complete(
                        "✅ Квитанция загружена!\n\nБухгалтер проверит её в ближайшее время.\nСтатус оплаты обновится после проверки."
                    )
                } catch (e: Exception) {
                    logger.severe("Ошибка загрузки квитанции: ${e.message}")
                    return CommandResult.Error("Ошибка загрузки квитанции: ${e.message}")
                }
            }
            else -> {
                return CommandResult.Cancel()
            }
        }
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
                        return arrayOf(url, ".jpg", "фото_квитанции.jpg")
                    }
                } else if ("doc" == type) {
                    val docObj = attachment.getJSONObject("doc")
                    if (docObj.has("url")) {
                        val url = docObj.getString("url")
                        var ext = docObj.optString("ext", "")
                        if (!ext.startsWith(".") && ext.isNotEmpty()) {
                            ext = ".$ext"
                        }
                        if (ext.isEmpty()) ext = ".pdf"
                        val fileName = docObj.optString("title", "квитанция$ext")
                        return arrayOf(url, ext, fileName)
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
            val tempFile = File.createTempFile("receipt_", ".tmp")
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
}
