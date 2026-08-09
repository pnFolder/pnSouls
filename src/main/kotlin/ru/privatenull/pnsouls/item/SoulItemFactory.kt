package ru.privatenull.pnsouls.item

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnsouls.model.ItemAppearance
import java.util.Locale

class SoulItemFactory(@Suppress("UNUSED_PARAMETER") plugin: JavaPlugin) {

    fun createModelItem(appearance: ItemAppearance): ItemStack = createAppearance(appearance)

    private fun createAppearance(appearance: ItemAppearance): ItemStack {
        val item = appearance.base64?.let(SkullFactory::create) ?: run {
            val material = Material.matchMaterial(appearance.material.trim().uppercase(Locale.ROOT)) ?: Material.STONE
            ItemStack(material)
        }
        val meta = item.itemMeta ?: return item
        appearance.customModelData?.let(meta::setCustomModelData)
        if (meta is LeatherArmorMeta) parseColor(appearance.leatherColor)?.let(meta::setColor)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE)
        item.itemMeta = meta
        if (appearance.glow) {
            @Suppress("DEPRECATION")
            val enchantment = Enchantment.getByName("DURABILITY")
            if (enchantment != null) item.addUnsafeEnchantment(enchantment, 1)
        }
        return item
    }

    private fun parseColor(raw: String?): Color? {
        val values = raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        return if (values.size == 3) Color.fromRGB(
            values[0].coerceIn(0, 255), values[1].coerceIn(0, 255), values[2].coerceIn(0, 255),
        ) else null
    }
}
