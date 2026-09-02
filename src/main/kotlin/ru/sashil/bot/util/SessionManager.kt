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
            // Приводим данные к типу Map<String, Any>
            val dataMap: Map<String, Any> = data as Map<String, Any>

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
        sessionCache[userId]?.let { sessionData ->
            command.setStep(userId, sessionData.step)
            val data = command.createData(userId)
            data.putAll(sessionData.data)
            return true
        }

        return try {
            val sql = "SELECT command_name, step, data FROM pool.bot_sessions WHERE user_id = ?"
            dbService.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, userId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val commandName = rs.getString("command_name")
                            val step = rs.getInt("step")
                            val jsonData = rs.getString("data")

                            if (commandName != command::class.java.simpleName) {
                                return false
                            }

                            command.setStep(userId, step)
                            val data = command.createData(userId)
                            if (jsonData != null && jsonData.isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST")
                                val parsed = mapper.readValue(jsonData, Map::class.java) as Map<String, Any>
                                data.putAll(parsed)
                            }

                            sessionCache[userId] = SessionData(commandName, step, data)
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            logger.warning("Не удалось восстановить сессию для user $userId: ${e.message}")
            false
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
        } catch (e: Exception) {
            logger.warning("Не удалось очистить сессию для user $userId: ${e.message}")
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