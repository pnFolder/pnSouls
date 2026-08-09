package ru.privatenull.pnsouls.config

import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.text.ColorUtil
import java.io.File

class Messages(private val plugin: JavaPlugin) {
    private lateinit var yaml: YamlConfiguration

    init {
        load()
    }

    fun load() {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) plugin.saveResource("messages.yml", false)
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    fun send(sender: CommandSender, path: String, vararg replacements: Pair<String, String>) {
        sender.sendMessage(render(yaml.getString(path) ?: path, *replacements))
    }

    fun sendList(sender: CommandSender, path: String) {
        yaml.getStringList(path).forEach { sender.sendMessage(render(it)) }
    }

    fun render(text: String, vararg replacements: Pair<String, String>): String {
        var value = text.replace("%prefix%", yaml.getString("prefix") ?: "")
        for ((key, replacement) in replacements) value = value.replace("%$key%", replacement)
        return ColorUtil.colorize(value)
    }
}
