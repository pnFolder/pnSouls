package ru.privatenull.pnsouls.runtime

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import ru.privatenull.pnsouls.model.SoulDefinition
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

class CropGrowthService {
    private var chances: Map<Material, Double> = emptyMap()
    private var verticalRadius: Int = 2

    fun load(config: FileConfiguration) {
        verticalRadius = config.getInt("farmer.vertical-scan-radius", 2).coerceIn(0, 8)
        val section = config.getConfigurationSection("farmer.crop-chances")
        chances = section?.getKeys(false).orEmpty().mapNotNull { raw ->
            val material = Material.matchMaterial(raw.uppercase(Locale.ROOT)) ?: return@mapNotNull null
            material to section!!.getDouble(raw, 0.0).coerceIn(0.0, 1.0)
        }.toMap()
    }

    fun growAround(player: Player, definition: SoulDefinition) {
        val ability = definition.cropGrowth ?: return
        val center = player.location.block
        val random = ThreadLocalRandom.current()
        for (x in -ability.radius..ability.radius) {
            for (z in -ability.radius..ability.radius) {
                if (x * x + z * z > ability.radius * ability.radius) continue
                for (y in -verticalRadius..verticalRadius) {
                    val block = center.getRelative(x, y, z)
                    val chance = chances[block.type] ?: continue
                    if (random.nextDouble() <= chance) grow(block, random)
                }
            }
        }
    }

    private fun grow(block: Block, random: ThreadLocalRandom) {
        val data = block.blockData
        if (data is Ageable) {
            if (data.age < data.maximumAge) {
                data.age++
                block.blockData = data
            } else if (block.type.name.endsWith("_STEM")) {
                growFruit(block, random)
            }
            return
        }

        when (block.type.name) {
            "CACTUS", "SUGAR_CANE", "BAMBOO" -> growVertical(block)
            "KELP" -> growKelp(block)
        }
    }

    private fun growVertical(block: Block) {
        val above = block.getRelative(BlockFace.UP)
        if (above.type.isAir) above.type = block.type
    }

    private fun growKelp(block: Block) {
        val above = block.getRelative(BlockFace.UP)
        if (above.type != Material.WATER) return
        Material.matchMaterial("KELP_PLANT")?.let { block.type = it }
        above.type = Material.KELP
    }

    private fun growFruit(stem: Block, random: ThreadLocalRandom) {
        val fruitName = if (stem.type.name.startsWith("MELON")) "MELON" else "PUMPKIN"
        val fruit = Material.matchMaterial(fruitName) ?: return
        val faces = listOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST).shuffled()
        val target = faces.asSequence().map(stem::getRelative)
            .firstOrNull { it.type.isAir && it.getRelative(BlockFace.DOWN).type.isSolid } ?: return
        target.type = fruit
    }
}
