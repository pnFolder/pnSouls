package ru.privatenull.pnsouls

import org.bstats.bukkit.Metrics
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService
import ru.privatenull.pnlibrary.gui.GuiUpdateService
import ru.privatenull.pnlibrary.gui.protocollib.ProtocolLibSlotPacketSender
import ru.privatenull.pnlibrary.lifecycle.PluginBanner
import ru.privatenull.pnlibrary.update.UpdateChecker
import ru.privatenull.pnlibrary.update.UpdateSettings
import ru.privatenull.pnsouls.command.SoulCommand
import ru.privatenull.pnsouls.config.Messages
import ru.privatenull.pnsouls.config.SoulRegistry
import ru.privatenull.pnsouls.gui.SoulListMenu
import ru.privatenull.pnsouls.item.SoulItemFactory
import ru.privatenull.pnsouls.runtime.CropGrowthService
import ru.privatenull.pnsouls.runtime.SoulListener
import ru.privatenull.pnsouls.runtime.SoulOwnershipService
import ru.privatenull.pnsouls.runtime.SoulSessionService
import ru.privatenull.pnsouls.visual.VisualManager

class PnSoulsPlugin : JavaPlugin() {
    private lateinit var messages: Messages
    private lateinit var registry: SoulRegistry
    private lateinit var items: SoulItemFactory
    private lateinit var visuals: VisualManager
    private lateinit var crops: CropGrowthService
    private lateinit var sessions: SoulSessionService
    private lateinit var ownership: SoulOwnershipService
    private lateinit var guiUpdates: GuiUpdateService
    private lateinit var guiAnimations: GuiOpenAnimationService
    private lateinit var menu: SoulListMenu
    private lateinit var updateChecker: UpdateChecker
    private var guiPacketSender: AutoCloseable? = null

    override fun onEnable() {
        saveDefaultConfig()
        messages = Messages(this)
        registry = SoulRegistry(this).also(SoulRegistry::load)
        items = SoulItemFactory(this)
        visuals = VisualManager(this, items).also {
            it.removeOrphans()
            it.start()
        }
        crops = CropGrowthService().also { it.load(config) }
        ownership = SoulOwnershipService(this)
        sessions = SoulSessionService(this, registry, messages, visuals).also { it.start() }
        val packetSender: GuiUpdateService.SlotPacketSender? =
            if (server.pluginManager.isPluginEnabled("ProtocolLib")) {
                try {
                    ProtocolLibSlotPacketSender(this).also { guiPacketSender = it }
                } catch (exception: RuntimeException) {
                    logger.warning("Не удалось включить ProtocolLib SET_SLOT: ${exception.message}")
                    null
                } catch (error: LinkageError) {
                    logger.warning("ProtocolLib несовместим с SET_SLOT: ${error.message}")
                    null
                }
            } else null
        guiUpdates = GuiUpdateService(packetSender)
        guiAnimations = GuiOpenAnimationService(this, guiUpdates)
        menu = SoulListMenu(this, registry, items, messages, ownership, sessions, guiAnimations, guiUpdates)
        updateChecker = UpdateChecker(this, updateSettings()).also(UpdateChecker::start)

        val listener = SoulListener(sessions, crops, updateChecker)
        server.pluginManager.registerEvents(listener, this)
        server.pluginManager.registerEvents(menu, this)
        val executor = SoulCommand(this, registry, messages, sessions, ownership, menu)
        val command: PluginCommand = getCommand("pnsouls")
            ?: error("Команда pnsouls отсутствует в plugin.yml")
        command.setExecutor(executor)
        command.tabCompleter = executor
        refreshCommandDispatcher()

        server.onlinePlayers.forEach(sessions::resume)
        Metrics(this, 33269)
        PluginBanner.enabled(this, SUPPORT_URL)
    }

    override fun onDisable() {
        if (::menu.isInitialized) menu.closeAll()
        if (::guiAnimations.isInitialized) guiAnimations.shutdown()
        try {
            guiPacketSender?.close()
        } catch (exception: Exception) {
            logger.warning("Не удалось остановить ProtocolLib SET_SLOT: ${exception.message}")
        }
        guiPacketSender = null
        if (::updateChecker.isInitialized) updateChecker.cancel()
        if (::sessions.isInitialized) sessions.shutdown()
        PluginBanner.disabled(this, SUPPORT_URL)
    }

    fun reloadPluginData() {
        reloadConfig()
        messages.load()
        registry.load()
        crops.load(config)
        if (::updateChecker.isInitialized) updateChecker.restart(updateSettings())
        menu.closeAll()
        menu.load()
        sessions.reloadOnlinePlayers()
    }

    /**
     * Rebuilds Paper's Brigadier tree after a dynamic load. PlugManX can leave
     * aliases pointing at the old disabled PluginCommand until the dispatcher
     * and every online player's command tree are refreshed.
     */
    private fun refreshCommandDispatcher() {
        server.scheduler.runTaskLater(this, Runnable {
            try {
                server.javaClass.getMethod("syncCommands").invoke(server)
            } catch (exception: ReflectiveOperationException) {
                logger.fine("Paper syncCommands недоступен: ${exception.message}")
            }
            for (player in server.onlinePlayers) {
                try {
                    player.javaClass.getMethod("updateCommands").invoke(player)
                } catch (exception: ReflectiveOperationException) {
                    logger.fine("Не удалось обновить команды игрока ${player.name}: ${exception.message}")
                }
            }
        }, 1L)
    }

    private fun updateSettings() = UpdateSettings(
        true,
        GITHUB_REPOSITORY,
        UPDATE_PERMISSION,
        UPDATE_CHECK_PERIOD_HOURS,
        SUPPORT_URL,
    )

    companion object {
        private const val GITHUB_REPOSITORY = "Dy6HiLa/pnSouls"
        private const val UPDATE_PERMISSION = "pnsouls.update"
        private const val UPDATE_CHECK_PERIOD_HOURS = 6L
        private const val SUPPORT_URL = "https://discord.gg/rRbzq6cnc6"
    }
}
