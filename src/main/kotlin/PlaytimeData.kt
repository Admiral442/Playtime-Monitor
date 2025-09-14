package com.playtime

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PlaytimeData(private val file: File) {
    private val config = YamlConfiguration()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun load() {
        if (file.exists()) {
            config.load(file)
        }
    }

    fun save() {
        try {
            config.save(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addPlaytime(uuid: UUID, playerName: String, milliseconds: Long) {
        val playerSection = "players.${uuid}"
        val currentPlaytime = config.getLong("${playerSection}.playtime", 0L)

        config.set("${playerSection}.name", playerName)
        config.set("${playerSection}.playtime", currentPlaytime + milliseconds)
    }

    fun getPlaytime(uuid: UUID): Long {
        return config.getLong("players.${uuid}.playtime", 0L)
    }

    fun setLastLogin(uuid: UUID, playerName: String, dateTime: LocalDateTime) {
        val playerSection = "players.${uuid}"
        config.set("${playerSection}.name", playerName)
        config.set("${playerSection}.lastLogin", dateTime.format(dateFormatter))
    }

    fun getLastLogin(uuid: UUID): LocalDateTime? {
        val lastLoginString = config.getString("players.${uuid}.lastLogin")
        return if (lastLoginString != null) {
            try {
                LocalDateTime.parse(lastLoginString, dateFormatter)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}
