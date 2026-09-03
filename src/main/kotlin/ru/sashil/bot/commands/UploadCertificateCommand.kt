package ru.sashil.bot.commands

import ru.sashil.common.service.DatabaseService
import ru.sashil.common.service.MinIOService
import ru.sashil.common.util.CommandUtils
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Logger

class UploadCertificateCommand(
    private val dbService: DatabaseService,
    private val minioService: MinIOService
) : BaseBotCommand() {

    override val displayName: String = "Прикрепить справку о допуске в бассейн"
    override val description: String = "Загрузка медицинской справки"

    private val logger = Logger.getLogger(UploadCertificateCommand::class.java.name)

    override fun start(userId: Long): CommandResult {
        setStep(userId, 1)
        createData(userId)
        return CommandResult.Continue(
            "Вы хотите прикрепить Справку от врача о допуске для занятий в плавательном бассейне?\n\n(Да/Нет)"
        )
    }

    override fun processMessage(userId: Long, text: String, rawJson: String?): CommandResult {
        val step = getStep(userId)
        val data = getData(userId) ?: return CommandResult.Error("Ошибка данных")
        val cmd = CommandUtils.normalize(text)

        if (cmd == "нет" || cmd == "отмена") {
            removeSession(userId)
            return CommandResult.Cancel()
        }

        when (step) {
            1 -> {
                if (cmd != "да") {
                    return CommandResult.Continue(
                        "Для возврата в главное меню напишите 'нет'.\n\n" +
                        "Вы хотите прикрепить Справку от врача о допуске для занятий в плавательном бассейне?"
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
                    setStep(userId, 3)
                    return CommandResult.Continue(
                        "Пожалуйста, выберите фото или pdf файл и нажмите отправить."
                    )
                } else {
                    setStep(userId, 2)
                    val sb = StringBuilder("Выберите ребенка, для которого Вы хотите прикрепить справку:\n\n")
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
                setStep(userId, 3)
                return CommandResult.Continue(
                    "Пожалуйста, выберите фото или pdf и нажмите отправить."
                )
            }
            3 -> {
                if (rawJson == null || !rawJson.contains("\"attachments\"")) {
                    return CommandResult.Continue("Я не вижу вложения. Пожалуйста, выберите фото или pdf и нажмите отправить.")
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

                    val url = minioService.uploadFile(file.absolutePath, "certificate$extension")
                    file.delete()

                    val childId = data["childId"] as Long
                    dbService.saveCertificate(userId, childId, url)

                    removeSession(userId)

                    return CommandResult.Complete(
                        "✅ Ваша справка получена! После проверки администратором Вы получите уведомление об успешной загрузке справки.\n\n" +
                        "Пожалуйста, принесите оригинал справки на занятие в бассейне и передайте тренеру. Благодарим!"
                    )
                } catch (e: Exception) {
                    logger.severe("Ошибка загрузки справки: ${e.message}")
                    return CommandResult.Error("Ошибка загрузки справки: ${e.message}")
                }
            }
            else -> {
                removeSession(userId)
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
        removeSession(userId)
        return CommandResult.Cancel()
    }
}
