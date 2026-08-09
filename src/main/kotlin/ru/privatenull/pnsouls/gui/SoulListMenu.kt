package ru.privatenull.pnsouls.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService
import ru.privatenull.pnlibrary.gui.GuiUpdateService
import ru.privatenull.pnlibrary.text.ColorUtil
import ru.privatenull.pnsouls.config.Messages
import ru.privatenull.pnsouls.config.SoulRegistry
import ru.privatenull.pnsouls.item.SkullFactory
import ru.privatenull.pnsouls.item.SoulItemFactory
import ru.privatenull.pnsouls.model.SoulDefinition
import ru.privatenull.pnsouls.runtime.SoulOwnershipService
import ru.privatenull.pnsouls.runtime.SoulSessionService
import java.io.File

class SoulListMenu(
    private val plugin: JavaPlugin,
    private val registry: SoulRegistry,
    private val items: SoulItemFactory,
    private val messages: Messages,
    private val ownership: SoulOwnershipService,
    private val sessions: SoulSessionService,
    private val animations: GuiOpenAnimationService,
    private val updates: GuiUpdateService,
) : Listener {
    private lateinit var config: YamlConfiguration
    private lateinit var layout: Layout

    init { load() }

    fun load() {
        val file = File(plugin.dataFolder, "gui.yml")
        if (!file.exists()) plugin.saveResource("gui.yml", false)
        config = YamlConfiguration.loadConfiguration(file)
        val root = config.getConfigurationSection("list") ?: error("gui.yml: отсутствует раздел list")
        val size = normalizeSize(root.getInt("size", 54))
        val content = validSlots(root.getIntegerList("content-slots"), size).ifEmpty { DEFAULT_SLOTS }
        layout = Layout(
            size, content,
            bounded(root.getInt("previous-page.slot", 45), size),
            bounded(root.getInt("page-info.slot", 4), size),
            bounded(root.getInt("next-page.slot", 53), size),
            bounded(root.getInt("close.slot", 49), size),
        )
    }

    fun open(player: Player) = open(player, 0, false)

    private fun open(player: Player, requestedPage: Int, pageChange: Boolean) {
        val definitions = registry.all().sortedBy(SoulDefinition::id)
        if (definitions.isEmpty()) return
        val pages = maxOf(1, kotlin.math.ceil(definitions.size / layout.content.size.toDouble()).toInt())
        val page = requestedPage.coerceIn(0, pages - 1)
        val desired = createInventory(player, definitions, page, pages)
        val current = player.openInventory.topInventory
        val holder = current.holder as? SoulListHolder
        if (pageChange && holder != null && current.size == desired.size) {
            animations.cancel(player)
            holder.page = page
            for (slot in 0 until current.size) {
                if (current.getItem(slot) != desired.getItem(slot)) {
                    updates.setTopSlot(player, current, slot, desired.getItem(slot))
                }
            }
        } else {
            animations.open(player, desired)
            play(player, Sound.BLOCK_BARREL_OPEN, 0.18f, 1.25f)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        val holder = top.holder as? SoulListHolder ?: return
        event.isCancelled = true
        val slot = event.rawSlot
        if (slot !in 0 until top.size) return
        val definitions = registry.all().sortedBy(SoulDefinition::id)
        val pages = maxOf(1, kotlin.math.ceil(definitions.size / layout.content.size.toDouble()).toInt())
        when {
            slot == layout.previous && holder.page > 0 -> {
                open(player, holder.page - 1, true)
                play(player, Sound.UI_BUTTON_CLICK, 0.14f, 1.2f)
                return
            }
            slot == layout.next && holder.page + 1 < pages -> {
                open(player, holder.page + 1, true)
                play(player, Sound.UI_BUTTON_CLICK, 0.14f, 1.2f)
                return
            }
            slot == layout.close -> {
                animations.cancel(player)
                player.closeInventory()
                play(player, Sound.BLOCK_BARREL_CLOSE, 0.18f, 1.25f)
                return
            }
        }
        if (!event.click.isLeftClick) return
        val local = layout.content.indexOf(slot)
        if (local < 0) return
        val definition = definitions.getOrNull(holder.page * layout.content.size + local) ?: return
        if (!ownership.owns(player, definition.id)) {
            messages.send(player, "not-owned")
            play(player, Sound.ENTITY_VILLAGER_NO, 0.22f, 1.1f)
            return
        }
        if (sessions.definition(player)?.id == definition.id) {
            sessions.deactivate(player)
        } else {
            sessions.activate(player, definition, replace = true)
            messages.send(player, "activated", "soul" to definition.name)
        }
        open(player, holder.page, true)
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.18f, 1.35f)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is SoulListHolder) return
        if (event.rawSlots.any { it in 0 until event.view.topInventory.size }) event.isCancelled = true
    }

    fun closeAll() {
        plugin.server.onlinePlayers.filter { it.openInventory.topInventory.holder is SoulListHolder }
            .forEach(Player::closeInventory)
    }

    private fun createInventory(
        player: Player,
        definitions: List<SoulDefinition>,
        page: Int,
        pages: Int,
    ): Inventory {
        val holder = SoulListHolder(page)
        val title = ColorUtil.colorize(config.getString("list.title", "&0Коллекция душ") ?: "&0Коллекция душ")
        val inventory = Bukkit.createInventory(holder, layout.size, title)
        holder.attach(inventory)
        val active = sessions.definition(player)
        val owned = ownership.owned(player)
        val placeholders = placeholders(page, pages, definitions.size, owned.size, active?.name ?: "&7Нет")
        val decor = configuredItem(config.getConfigurationSection("list.decor"), " ", emptyList(), placeholders)
        for (slot in 0 until inventory.size) inventory.setItem(slot, decor.clone())

        val start = page * layout.content.size
        for (local in layout.content.indices) {
            val definition = definitions.getOrNull(start + local) ?: break
            val entry = items.createModelItem(definition.model.head)
            val extraPath = when {
                active?.id == definition.id -> "list.entry-lore-active"
                definition.id in owned -> "list.entry-lore-owned"
                else -> "list.entry-lore-locked"
            }
            val meta = entry.itemMeta
            if (meta != null) {
                meta.setDisplayName(ColorUtil.colorize(definition.name))
                meta.lore = (definition.lore + config.getStringList(extraPath)).map(ColorUtil::colorize)
                meta.addItemFlags(*ItemFlag.values())
                entry.itemMeta = meta
            }
            inventory.setItem(layout.content[local], entry)
        }

        val previousPath = if (page > 0) "list.previous-page" else "list.previous-page-disabled"
        val nextPath = if (page + 1 < pages) "list.next-page" else "list.next-page-disabled"
        inventory.setItem(layout.previous, configuredItem(config.getConfigurationSection(previousPath), "&8Назад", emptyList(), placeholders))
        inventory.setItem(layout.next, configuredItem(config.getConfigurationSection(nextPath), "&8Вперёд", emptyList(), placeholders))
        inventory.setItem(layout.info, configuredItem(config.getConfigurationSection("list.page-info"), "&#FC7165Коллекция душ", emptyList(), placeholders))
        inventory.setItem(layout.close, configuredItem(config.getConfigurationSection("list.close"), "&#FF2310Закрыть", emptyList(), placeholders))
        return inventory
    }

    private fun configuredItem(
        section: ConfigurationSection?,
        fallbackName: String,
        fallbackLore: List<String>,
        placeholders: Map<String, String> = emptyMap(),
    ): ItemStack {
        val base64 = section?.getString("base64")
        val material = Material.matchMaterial(section?.getString("material", "GRAY_STAINED_GLASS_PANE")
            ?: "GRAY_STAINED_GLASS_PANE") ?: Material.GRAY_STAINED_GLASS_PANE
        val item = if (base64.isNullOrBlank()) ItemStack(material) else SkullFactory.create(base64)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ColorUtil.colorize(replace(section?.getString("name", fallbackName) ?: fallbackName, placeholders)))
        val lore = if (section?.isList("lore") == true) section.getStringList("lore") else fallbackLore
        meta.lore = lore.map { ColorUtil.colorize(replace(it, placeholders)) }.ifEmpty { null }
        meta.addItemFlags(*ItemFlag.values())
        item.itemMeta = meta
        return item
    }

    private fun placeholders(page: Int, pages: Int, amount: Int, owned: Int, active: String) = mapOf(
        "page" to (page + 1).toString(), "pages" to pages.toString(), "amount" to amount.toString(),
        "owned" to owned.toString(), "active" to active,
    )

    private fun replace(value: String, placeholders: Map<String, String>): String {
        var result = value
        placeholders.forEach { (key, replacement) -> result = result.replace("{$key}", replacement) }
        return result
    }

    private fun play(player: Player, sound: Sound, volume: Float, pitch: Float) =
        player.playSound(player.location, sound, volume, pitch)

    private fun normalizeSize(value: Int) = (value.coerceIn(9, 54) / 9) * 9
    private fun bounded(value: Int, size: Int) = value.coerceIn(0, size - 1)
    private fun validSlots(values: List<Int>, size: Int) = values.filter { it in 0 until size }.distinct()

    private data class Layout(
        val size: Int,
        val content: List<Int>,
        val previous: Int,
        val info: Int,
        val next: Int,
        val close: Int,
    )

    companion object {
        private val DEFAULT_SLOTS = listOf(20, 22, 24, 29, 31, 33)
    }
}
