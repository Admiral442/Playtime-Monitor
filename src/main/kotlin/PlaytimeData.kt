package com.playtime

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.Node
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PlaytimeData(private val file: File) {
    private val config = YamlConfiguration()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val luckPerms: LuckPerms by lazy { LuckPermsProvider.get() }

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
        val newPlaytime = currentPlaytime + milliseconds

        config.set("${playerSection}.name", playerName)
        config.set("${playerSection}.playtime", newPlaytime)

        updatePlaytimePermissions(uuid, newPlaytime)
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
        return if (!lastLoginString.isNullOrEmpty()) {
            try {
                LocalDateTime.parse(lastLoginString, dateFormatter)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun updatePlaytimePermissions(uuid: UUID, playtimeMillis: Long) {
        val user = luckPerms.userManager.getUser(uuid) ?: return
        val hoursPlayed = playtimeMillis / (1000 * 60 * 60)

        // Remove old playtime permissions
        user.data().clear { node ->
            node.key.startsWith("playtime.hours.")
        }

        // Add new playtime permission
        val playtimeNode = Node.builder("playtime.hours.$hoursPlayed").build()
        user.data().add(playtimeNode)

        luckPerms.userManager.saveUser(user)
    }

    fun hasPlaytimePermission(player: Player, hours: Long): Boolean {
        return player.hasPermission("playtime.hours.$hours")
    }
}
