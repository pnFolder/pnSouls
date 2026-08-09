package ru.privatenull.pnsouls.runtime

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class SoulOwnershipService(plugin: JavaPlugin) {
    private val ownedKey = NamespacedKey(plugin, "owned_souls")

    fun owns(player: Player, soulId: String): Boolean = soulId.lowercase(Locale.ROOT) in owned(player)

    fun owned(player: Player): Set<String> = player.persistentDataContainer
        .get(ownedKey, PersistentDataType.STRING)
        .orEmpty()
        .split(',')
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { it.lowercase(Locale.ROOT) }
        .toCollection(linkedSetOf())

    fun grant(player: Player, soulId: String): Boolean {
        val values = owned(player).toMutableSet()
        if (!values.add(soulId.lowercase(Locale.ROOT))) return false
        save(player, values)
        return true
    }

    fun revoke(player: Player, soulId: String): Boolean {
        val values = owned(player).toMutableSet()
        if (!values.remove(soulId.lowercase(Locale.ROOT))) return false
        save(player, values)
        return true
    }

    private fun save(player: Player, values: Set<String>) {
        if (values.isEmpty()) player.persistentDataContainer.remove(ownedKey)
        else player.persistentDataContainer.set(ownedKey, PersistentDataType.STRING, values.sorted().joinToString(","))
    }
}
