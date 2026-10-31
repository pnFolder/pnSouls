package ru.privatenull.pnsouls.bootstrap

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import ru.privatenull.pnsouls.PnSoulsPlugin

/** Не даёт pnSouls пережить общий рантайм при остановке сервера или ручной выгрузке. */
class PnLibraryLifecycleListener(private val plugin: PnSoulsPlugin) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginDisable(event: PluginDisableEvent) {
        if (!event.plugin.name.equals("pnLibrary", ignoreCase = true) || !plugin.isEnabled) return
        plugin.logger.warning("pnLibrary отключается; pnSouls будет безопасно остановлен первым.")
        plugin.server.pluginManager.disablePlugin(plugin)
    }
}
