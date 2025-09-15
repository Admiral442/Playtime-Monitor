package com.playtime

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PlaytimeMonitor : JavaPlugin(), Listener {

    private val playerSessions = ConcurrentHashMap<UUID, Long>()
    private val dataFile = File(dataFolder, "playtime.yml")
    private lateinit var playtimeData: PlaytimeData

    override fun onEnable() {
        dataFolder.mkdirs()
        playtimeData = PlaytimeData(dataFile)
        playtimeData.load()

        server.pluginManager.registerEvents(this, this)

        // Start existing players' sessions
        Bukkit.getOnlinePlayers().forEach { player ->
            playerSessions[player.uniqueId] = System.currentTimeMillis()
        }

        logger.info("PlaytimeMonitor has been enabled!")
    }

    override fun onDisable() {
        // Save all current sessions before shutdown
        saveAllSessions()
        playtimeData.save()
        logger.info("PlaytimeMonitor has been disabled!")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        playerSessions[player.uniqueId] = System.currentTimeMillis()

        // Update last login time
        playtimeData.setLastLogin(player.uniqueId, player.name, LocalDateTime.now())
        playtimeData.save()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        savePlayerSession(player)
        playerSessions.remove(player.uniqueId)
    }

    private fun savePlayerSession(player: Player) {
        val sessionStart = playerSessions[player.uniqueId] ?: return
        val sessionDuration = System.currentTimeMillis() - sessionStart
        playtimeData.addPlaytime(player.uniqueId, player.name, sessionDuration)
        playtimeData.save()
    }

    private fun saveAllSessions() {
        Bukkit.getOnlinePlayers().forEach { player ->
            savePlayerSession(player)
            // Restart session for still online players
            playerSessions[player.uniqueId] = System.currentTimeMillis()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (command.name.lowercase()) {
            "playtime" -> {
                if (args.isEmpty()) {
                    // Show sender's playtime
                    if (sender is Player) {
                        if (!sender.hasPermission("playtime.check")) {
                            sender.sendMessage("${ChatColor.RED}You do not have permission to check your playtime.")
                            return true
                        }
                        showPlaytime(sender, sender)
                    } else {
                        sender.sendMessage("${ChatColor.RED}Console must specify a player name!")
                    }
                } else {
                    // Show specified player's playtime
                    val targetName = args[0]
                    val target = Bukkit.getPlayer(targetName)

                    if (sender is Player && sender.name.equals(targetName, ignoreCase = true)) {
                        // Player checking their own playtime
                        if (!sender.hasPermission("playtime.check")) {
                            sender.sendMessage("${ChatColor.RED}You do not have permission to check your playtime.")
                            return true
                        }
                    } else if (sender is Player && !sender.hasPermission("playtime.check.others")) {
                        sender.sendMessage("${ChatColor.RED}You do not have permission to check other players' playtime.")
                        return true
                    }

                    if (target != null) {
                        showPlaytime(sender, target)
                    } else {
                        // Try to find offline player
                        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
                        if (offlinePlayer.hasPlayedBefore()) {
                            showOfflinePlaytime(sender, offlinePlayer.uniqueId, offlinePlayer.name ?: targetName)
                        } else {
                            sender.sendMessage("${ChatColor.RED}Player '$targetName' not found!")
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    private fun showPlaytime(sender: CommandSender, target: Player) {
        // Save current session for accurate data
        savePlayerSession(target)
        playerSessions[target.uniqueId] = System.currentTimeMillis()

        val totalPlaytime = playtimeData.getPlaytime(target.uniqueId)
        val lastLogin = playtimeData.getLastLogin(target.uniqueId)

        sendPlaytimeMessage(sender, target.name, totalPlaytime, lastLogin)
    }

    private fun showOfflinePlaytime(sender: CommandSender, uuid: UUID, name: String) {
        val totalPlaytime = playtimeData.getPlaytime(uuid)
        val lastLogin = playtimeData.getLastLogin(uuid)

        sendPlaytimeMessage(sender, name, totalPlaytime, lastLogin)
    }

    private fun sendPlaytimeMessage(sender: CommandSender, playerName: String, totalPlaytime: Long, lastLogin: LocalDateTime?) {
        val timeFormatted = formatPlaytime(totalPlaytime)
        val lastLoginFormatted = lastLogin?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Never"

        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}-----------------------------------")
        sender.sendMessage("${ChatColor.YELLOW}Playtime: ${ChatColor.WHITE}$playerName")
        sender.sendMessage("${ChatColor.YELLOW}Time: ${ChatColor.WHITE}$timeFormatted")
        sender.sendMessage("${ChatColor.YELLOW}Last Login: ${ChatColor.WHITE}$lastLoginFormatted")
        sender.sendMessage("${ChatColor.GOLD}-----------------------------------")
        sender.sendMessage("")
    }

    private fun formatPlaytime(milliseconds: Long): String {
        val totalMinutes = milliseconds / (1000 * 60)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60

        return "${days}:${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
    }
}
