package ru.privatenull.pnsouls.visual

import org.bukkit.Bukkit
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import ru.privatenull.pnsouls.item.SoulItemFactory
import ru.privatenull.pnsouls.model.SoulDefinition
import java.util.UUID

class VisualManager(
    private val plugin: JavaPlugin,
    private val items: SoulItemFactory,
) {
    private val visuals = hashMapOf<UUID, SoulVisual>()
    private var task: BukkitTask? = null
    private var tick = 0L
    private lateinit var settings: VisualSettings

    fun start() {
        stopTask()
        settings = VisualSettings.from(plugin.config)
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            tick += settings.updatePeriod
            visuals.values.toList().forEach { it.tick(tick) }
        }, 1L, settings.updatePeriod)
    }

    fun show(player: Player, definition: SoulDefinition) {
        hide(player.uniqueId)
        SoulVisual(plugin, player, definition, items, settings).also {
            visuals[player.uniqueId] = it
            it.spawn(tick)
        }
    }

    fun hide(playerId: UUID) {
        visuals.remove(playerId)?.remove()
    }

    fun removeOrphans() {
        for (world in Bukkit.getWorlds()) {
            world.entities.asSequence()
                .filterIsInstance<ArmorStand>()
                .filter { it.scoreboardTags.contains(VISUAL_TAG) }
                .forEach(ArmorStand::remove)
        }
    }

    fun shutdown() {
        stopTask()
        visuals.values.forEach(SoulVisual::remove)
        visuals.clear()
    }

    private fun stopTask() {
        task?.cancel()
        task = null
    }

    companion object {
        const val VISUAL_TAG = "pnsouls_visual"
    }
}
