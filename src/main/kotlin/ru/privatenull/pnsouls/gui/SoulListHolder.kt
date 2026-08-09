package ru.privatenull.pnsouls.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class SoulListHolder(var page: Int) : InventoryHolder {
    private var backing: Inventory? = null
    fun attach(inventory: Inventory) { backing = inventory }
    override fun getInventory(): Inventory = requireNotNull(backing)
}
