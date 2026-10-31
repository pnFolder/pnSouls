package ru.privatenull.pnsouls.runtime

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import ru.privatenull.pnsouls.visual.VisualManager
import java.util.concurrent.ThreadLocalRandom

class SoulListener(
    private val sessions: SoulSessionService,
    private val crops: CropGrowthService,
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSoulVisualDamage(event: EntityDamageEvent) {
        if (event.entity.scoreboardTags.contains(VisualManager.VISUAL_TAG)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSoulVisualManipulate(event: PlayerArmorStandManipulateEvent) {
        if (event.rightClicked.scoreboardTags.contains(VisualManager.VISUAL_TAG)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        sessions.resume(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = sessions.onQuit(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        sessions.definition(event.player)?.let { sessions.resume(event.player) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        sessions.onDeath(event.entity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) = sessions.resumeNextTick(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMobDeath(event: EntityDeathEvent) {
        if (event.entity is Player) return
        val killer = event.entity.killer ?: return
        val definition = sessions.definition(killer) ?: return
        definition.mobDrops?.let { ability ->
            if (ThreadLocalRandom.current().nextDouble() <= ability.chance) {
                val original = event.drops.map(ItemStack::clone)
                for (item in original) {
                    var extra = (item.amount * (ability.multiplier - 1.0)).toInt()
                    while (extra > 0) {
                        val addition = item.clone()
                        addition.amount = extra.coerceAtMost(addition.maxStackSize)
                        event.drops.add(addition)
                        extra -= addition.amount
                    }
                }
            }
        }
        val multiplier = definition.mobExperienceMultiplier
        if (multiplier == 1.0) return
        event.droppedExp = (event.droppedExp * multiplier).toInt().coerceAtLeast(0)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFood(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        val definition = sessions.definition(player) ?: return
        if (!definition.infiniteFood) return
        if (event.foodLevel >= player.foodLevel) return
        event.isCancelled = true
        player.foodLevel = 20
        player.saturation = 20f
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        val from = event.from
        if (from.world?.uid == to.world?.uid && from.blockX == to.blockX &&
            from.blockY == to.blockY && from.blockZ == to.blockZ) return
        val definition = sessions.definition(event.player) ?: return
        if (definition.cropGrowth == null) return
        crops.growAround(event.player, definition)
    }

}
