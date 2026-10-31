package ru.privatenull.pnsouls.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.privatenull.pnsouls.support.text.ColorUtil
import ru.privatenull.pnsouls.PnSoulsPlugin
import ru.privatenull.pnsouls.config.Messages
import ru.privatenull.pnsouls.config.SoulRegistry
import ru.privatenull.pnsouls.gui.SoulListMenu
import ru.privatenull.pnsouls.runtime.SoulOwnershipService
import ru.privatenull.pnsouls.runtime.SoulSessionService
import java.util.Locale

class SoulCommand(
    private val plugin: PnSoulsPlugin,
    private val registry: SoulRegistry,
    private val messages: Messages,
    private val sessions: SoulSessionService,
    private val ownership: SoulOwnershipService,
    private val menu: SoulListMenu,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (label.equals("souls", true) && (args.isEmpty() || args[0].equals("list", true))) {
            if (sender is Player) openMenu(sender) else messages.sendList(sender, "usage")
            return true
        }
        if (sender is Player && !sender.hasPermission("pnsouls.admin")) {
            if (args.isEmpty() || args[0].equals("list", true) || args[0].equals("help", true)) openMenu(sender)
            else messages.send(sender, "no-permission")
            return true
        }
        when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
            null -> if (sender is Player) openMenu(sender) else messages.sendList(sender, "usage")
            "help" -> messages.sendList(sender, "usage")
            "list" -> list(sender)
            "give" -> give(sender, args)
            "take" -> take(sender, args)
            "reload" -> reload(sender)
            else -> messages.sendList(sender, "usage")
        }
        return true
    }

    private fun list(sender: CommandSender) {
        if (sender is Player) {
            openMenu(sender)
            return
        }
        sender.sendMessage(ColorUtil.colorize("&d&lДоступные души:"))
        val player = sender as? Player
        registry.all().forEach {
            val status = if (player != null && ownership.owns(player, it.id)) " &a✓" else ""
            sender.sendMessage(ColorUtil.colorize(" &8◆ ${it.name} &8(&7${it.id}&8)$status"))
        }
    }

    private fun openMenu(player: Player) {
        if (!player.hasPermission("pnsouls.use")) return messages.send(player, "no-permission")
        menu.open(player)
    }

    private fun give(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pnsouls.admin")) return messages.send(sender, "no-permission")
        if (args.size < 3) return messages.sendList(sender, "usage")
        val player = Bukkit.getPlayerExact(args[1]) ?: return messages.send(sender, "player-not-found")
        val definition = registry[args[2]]
            ?: return messages.send(sender, "unknown-soul", "id" to args[2])
        if (!ownership.grant(player, definition.id)) return messages.send(sender, "already-owned")
        messages.send(sender, "given", "soul" to definition.name, "player" to player.name)
        messages.send(player, "received", "soul" to definition.name, "id" to definition.id)
    }

    private fun take(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pnsouls.admin")) return messages.send(sender, "no-permission")
        if (args.size < 3) return messages.sendList(sender, "usage")
        val player = Bukkit.getPlayerExact(args[1]) ?: return messages.send(sender, "player-not-found")
        val definition = registry[args[2]]
            ?: return messages.send(sender, "unknown-soul", "id" to args[2])
        if (!ownership.revoke(player, definition.id)) return messages.send(sender, "player-not-owned")
        if (sessions.definition(player)?.id == definition.id) sessions.deactivate(player, notify = false)
        messages.send(sender, "removed", "soul" to definition.name, "player" to player.name)
    }

    private fun reload(sender: CommandSender) {
        if (!sender.hasPermission("pnsouls.admin")) return messages.send(sender, "no-permission")
        plugin.reloadPluginData()
        messages.send(sender, "reloaded")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        val values = when (args.size) {
            1 -> buildList {
                add("list")
                if (sender.hasPermission("pnsouls.admin")) addAll(listOf("give", "take", "reload"))
            }
            2 -> when {
                (args[0].equals("give", true) || args[0].equals("take", true)) &&
                    sender.hasPermission("pnsouls.admin") -> Bukkit.getOnlinePlayers().map(Player::getName)
                else -> emptyList()
            }
            3 -> if ((args[0].equals("give", true) || args[0].equals("take", true)) &&
                sender.hasPermission("pnsouls.admin"))
                registry.commandNames() else emptyList()
            else -> emptyList()
        }
        val prefix = args.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
        return values.filter { it.lowercase(Locale.ROOT).startsWith(prefix) }.sorted()
    }
}
