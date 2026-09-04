package ru.sashil.bot.util

import com.fasterxml.jackson.databind.ObjectMapper
import ru.sashil.bot.commands.BaseBotCommand
import ru.sashil.common.service.DatabaseService
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class SessionManager(
    private val dbService: DatabaseService
) {
    private val logger = Logger.getLogger(SessionManager::class.java.name)
    private val mapper = ObjectMapper()
    private val sessionCache = ConcurrentHashMap<Long, SessionData>()

    fun saveSession(userId: Long, command: BaseBotCommand) {
        val step = command.getStep(userId)
        val data = command.getData(userId) ?: return
        try {
            @Suppress("UNCHECKED_CAST")
            val dataMap = data as Map<String, Any>
            val sessionData = SessionData(
                commandName = command::class.java.simpleName,
                step = step,
                data = dataMap
            )
            sessionCache[userId] = sessionData

            val sql = """
                INSERT INTO pool.bot_sessions (user_id, command_name, step, data, updated_at)
                VALUES (?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET
                    command_name = EXCLUDED.command_name,
                    step = EXCLUDED.step,
                    data = EXCLUDED.data,
                    updated_at = CURRENT_TIMESTAMP
            """.trimIndent()

            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, userId)
                    stmt.setString(2, sessionData.commandName)
                    stmt.setInt(3, sessionData.step)
                    stmt.setString(4, mapper.writeValueAsString(sessionData.data))
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logger.warning("Не удалось сохранить сессию для user $userId: ${e.message}")
        }
    }

    fun restoreSession(userId: Long, command: BaseBotCommand): Boolean {
        // Сначала проверяем кэш (данные в кэше уже нормализованы)
        sessionCache[userId]?.let { sessionData ->
            command.setStep(userId, sessionData.step)
            val data = command.createData(userId)
            data.putAll(sessionData.data)
            logger.fine("Сессия восстановлена из кэша для $userId: step=${sessionData.step}")
            return true
        }

        // Читаем из БД
        return try {
            val sql = "SELECT command_name, step, data FROM pool.bot_sessions WHERE user_id = ?"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, userId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val dbCommandName = rs.getString("command_name")?.trim() ?: ""
                            val step = rs.getInt("step")
                            val jsonData = rs.getString("data")

                            val expectedName = command::class.java.simpleName
                            logger.info("Восстановление сессии $userId: DB='$dbCommandName', Expected='$expectedName', step=$step")

                            if (dbCommandName != expectedName) {
                                logger.warning("Несоответствие имен команд при восстановлении $userId: " +
                                        "в БД='$dbCommandName', ожидается='$expectedName'. Восстанавливаю.")
                            }

                            command.setStep(userId, step)
                            val data = command.createData(userId)

                            if (!jsonData.isNullOrBlank()) {
                                @Suppress("UNCHECKED_CAST")
                                val parsed = mapper.readValue(jsonData, Map::class.java) as Map<String, Any>
                                // === КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: нормализуем все числа в Long ===
                                // Явно приводим результат к Map<String, Any>, так как normalizeNumbers
                                // сохраняет структуру Map, но меняет типы значений
                                @Suppress("UNCHECKED_CAST")
                                val normalized = normalizeNumbers(parsed) as Map<String, Any>
                                data.putAll(normalized)
                            }

                            sessionCache[userId] = SessionData(dbCommandName, step, data)
                            logger.info("✅ Сессия успешно восстановлена из БД для $userId: step=$step, dataKeys=${data.keys}")
                            return true
                        }
                    }
                }
            }
            logger.warning("Запись сессии не найдена в БД для $userId")
            false
        } catch (e: Exception) {
            logger.severe("❌ Ошибка восстановления сессии для $userId: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Рекурсивно преобразует все Number в Map/List в Long.
     * Jackson десериализует маленькие числа как Integer, большие как Long.
     * Это вызывает ClassCastException при касте (data["id"] as Long).
     *
     * ВАЖНО: Для Map входного типа Map<String, Any> возвращаемый тип также будет Map<String, Any>,
     * но компилятор не может это вывести из-за рекурсии, поэтому требуется явный каст в месте вызова.
     */
    private fun normalizeNumbers(obj: Any?): Any? {
        return when (obj) {
            is Int -> obj.toLong()
            is Short -> obj.toLong()
            is Byte -> obj.toLong()
            is Float -> obj.toDouble()
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                obj.entries.associate { (k, v) ->
                    k.toString() to normalizeNumbers(v)
                } as Map<String, Any>
            }
            is List<*> -> obj.map { normalizeNumbers(it) }
            else -> obj
        }
    }

    fun clearSession(userId: Long) {
        sessionCache.remove(userId)
        try {
            val sql = "DELETE FROM pool.bot_sessions WHERE user_id = ?"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, userId)
                    stmt.executeUpdate()
                }
            }
            logger.info("Сессия очищена для $userId")
        } catch (e: Exception) {
            logger.warning("Не удалось очистить сессию для $userId: ${e.message}")
        }
    }

    fun hasSession(userId: Long): Boolean {
        if (sessionCache.containsKey(userId)) return true
        return try {
            val sql = "SELECT 1 FROM pool.bot_sessions WHERE user_id = ?"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, userId)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    data class SessionData(
        val commandName: String,
        val step: Int,
        val data: Map<String, Any>
    )
}