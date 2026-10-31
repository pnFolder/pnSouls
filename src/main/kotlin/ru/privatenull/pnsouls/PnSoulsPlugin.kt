package ru.privatenull.pnsouls

import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap
import ru.privatenull.pnsouls.support.gui.GuiOpenAnimationService
import ru.privatenull.pnsouls.support.gui.GuiUpdateService
import ru.privatenull.pnsouls.support.gui.protocollib.ProtocolLibSlotPacketSender
import ru.privatenull.pnsouls.bootstrap.PnLibraryLifecycleListener
import ru.privatenull.pnsouls.command.SoulCommand
import ru.privatenull.pnsouls.config.Messages
import ru.privatenull.pnsouls.config.SoulRegistry
import ru.privatenull.pnsouls.gui.SoulListMenu
import ru.privatenull.pnsouls.integration.PnLibraryRuntime
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
    private var pnRuntime: PnLibraryRuntime? = null
    private var pnLibraryReady = false
    private var guiPacketSender: AutoCloseable? = null

    override fun onLoad() {
        try {
            pnLibraryReady = PnLibraryBootstrap.ensureInstalled(this, PnLibraryRuntime.MINIMUM_VERSION)
        } catch (failure: Throwable) {
            logger.log(java.util.logging.Level.SEVERE,
                "Не удалось автоматически подготовить pnLibrary. pnSouls не будет запущен.", failure)
        }
    }

    override fun onEnable() {
        if (!pnLibraryReady) {
            logger.severe("pnLibrary не подготовлена: проверьте доступ к GitHub и ошибку загрузки выше.")
            server.pluginManager.disablePlugin(this)
            return
        }
        try {
            pnRuntime = PnLibraryRuntime.connect(this)
        } catch (failure: Throwable) {
            logger.log(java.util.logging.Level.SEVERE, "pnLibrary не удалось включить или подключить к pnSouls.", failure)
            server.pluginManager.disablePlugin(this)
            return
        }
        server.pluginManager.registerEvents(PnLibraryLifecycleListener(this), this)
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
        pnRuntime?.register { registry.all().size }

        val listener = SoulListener(sessions, crops)
        server.pluginManager.registerEvents(listener, this)
        server.pluginManager.registerEvents(menu, this)
        val executor = SoulCommand(this, registry, messages, sessions, ownership, menu)
        val command: PluginCommand = getCommand("pnsouls")
            ?: error("Команда pnsouls отсутствует в plugin.yml")
        command.setExecutor(executor)
        command.tabCompleter = executor
        refreshCommandDispatcher()

        server.onlinePlayers.forEach(sessions::resume)
        pnRuntime?.enabled(registry.all().size)
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
        if (::sessions.isInitialized) sessions.shutdown()
        val closing = pnRuntime
        pnRuntime = null
        try {
            closing?.close()
        } catch (failure: RuntimeException) {
            logger.log(java.util.logging.Level.WARNING, "Не удалось корректно закрыть контекст pnLibrary", failure)
        }
    }

    fun reloadPluginData() {
        reloadConfig()
        messages.load()
        registry.load()
        crops.load(config)
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

}
