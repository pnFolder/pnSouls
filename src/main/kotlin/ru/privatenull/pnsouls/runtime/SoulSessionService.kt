package ru.privatenull.pnsouls.runtime

import ru.privatenull.pnsouls.integration.Log

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import ru.privatenull.pnsouls.config.Messages
import ru.privatenull.pnsouls.config.SoulRegistry
import ru.privatenull.pnsouls.model.SoulDefinition
import ru.privatenull.pnsouls.visual.VisualManager
import java.util.UUID

class SoulSessionService(
    private val plugin: JavaPlugin,
    private val registry: SoulRegistry,
    private val messages: Messages,
    private val visuals: VisualManager,
) {
    private val activeKey = NamespacedKey(plugin, "active_soul")
    private val sessions = hashMapOf<UUID, SoulDefinition>()
    private val appliedEffects = hashMapOf<UUID, Set<PotionEffectType>>()
    private var effectTask: BukkitTask? = null

    fun start() {
        effectTask?.cancel()
        effectTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach(::refreshEffects)
        }, 1L, 20L)
    }

    fun activate(player: Player, definition: SoulDefinition, replace: Boolean = false): Boolean {
        if (definition(player) != null) {
            if (!replace) return false
            clear(player)
        }
        sessions[player.uniqueId] = definition
        player.persistentDataContainer.set(activeKey, PersistentDataType.STRING, definition.id)
        visuals.show(player, definition)
        if (definition.infiniteFood) {
            player.foodLevel = 20
            player.saturation = 20f
        }
        refreshEffects(player)
        return true
    }

    fun resume(player: Player) {
        val storedId = player.persistentDataContainer.get(activeKey, PersistentDataType.STRING) ?: return
        val definition = registry[storedId]
        if (definition == null) {
            player.persistentDataContainer.remove(activeKey)
            Log.warning("Не удалось восстановить неизвестную душу '$storedId' игроку ${player.name}")
            return
        }
        sessions[player.uniqueId] = definition
        visuals.show(player, definition)
        refreshEffects(player)
    }

    fun onQuit(player: Player) {
        visuals.hide(player.uniqueId)
        removeAppliedEffects(player)
        sessions.remove(player.uniqueId)
    }

    fun deactivate(player: Player, notify: Boolean = true): Boolean {
        if (definition(player) == null) return false
        clear(player)
        if (notify) messages.send(player, "deactivated")
        return true
    }

    fun onDeath(player: Player) {
        visuals.hide(player.uniqueId)
        removeAppliedEffects(player)
    }

    fun resumeNextTick(player: Player) {
        Bukkit.getScheduler().runTask(plugin, Runnable { if (player.isOnline) resume(player) })
    }

    fun definition(player: Player): SoulDefinition? = sessions[player.uniqueId]
        ?: registry[player.persistentDataContainer.get(activeKey, PersistentDataType.STRING)]

    fun refreshEffects(player: Player) {
        val definition = definition(player)
        if (definition == null || !player.isOnline || player.isDead) {
            removeAppliedEffects(player)
            return
        }
        val nowApplied = linkedSetOf<PotionEffectType>()
        for ((type, level) in definition.potionEffects) {
            player.addPotionEffect(PotionEffect(type, 50, level - 1, true, false, false), true)
            nowApplied += type
        }
        appliedEffects[player.uniqueId] = nowApplied
    }

    fun reloadOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach(::removeAppliedEffects)
        visuals.shutdown()
        visuals.start()
        sessions.clear()
        appliedEffects.clear()
        Bukkit.getOnlinePlayers().forEach(::resume)
    }

    fun shutdown() {
        effectTask?.cancel()
        effectTask = null
        Bukkit.getOnlinePlayers().forEach(::removeAppliedEffects)
        visuals.shutdown()
        sessions.clear()
    }

    private fun clear(player: Player) {
        sessions.remove(player.uniqueId)
        player.persistentDataContainer.remove(activeKey)
        visuals.hide(player.uniqueId)
        removeAppliedEffects(player)
    }

    private fun removeAppliedEffects(player: Player) {
        val types = appliedEffects.remove(player.uniqueId).orEmpty()
        for (type in types) {
            val current = player.getPotionEffect(type) ?: continue
            if (current.isAmbient && current.duration <= 60) player.removePotionEffect(type)
        }
    }

}
