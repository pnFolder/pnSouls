package ru.privatenull.pnsouls.config

import ru.privatenull.pnsouls.integration.Log

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import ru.privatenull.pnsouls.model.CropGrowthAbility
import ru.privatenull.pnsouls.model.ItemAppearance
import ru.privatenull.pnsouls.model.ModelPose
import ru.privatenull.pnsouls.model.MobDropAbility
import ru.privatenull.pnsouls.model.SoulDefinition
import ru.privatenull.pnsouls.model.SoulModel
import ru.privatenull.pnsouls.model.TrailDefinition
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

class SoulRegistry(private val plugin: JavaPlugin) {
    private var definitions: Map<String, SoulDefinition> = emptyMap()
    private var lookup: Map<String, SoulDefinition> = emptyMap()

    fun load() {
        val file = File(plugin.dataFolder, "souls.yml")
        if (!file.exists()) plugin.saveResource("souls.yml", false)
        val yaml = YamlConfiguration.loadConfiguration(file)
        val root = yaml.getConfigurationSection("souls")
        val bundledYaml = plugin.getResource("souls.yml")?.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use(YamlConfiguration::loadConfiguration)
        }
        val bundledRoot = bundledYaml?.getConfigurationSection("souls")
        if (root == null && bundledRoot == null) error("В souls.yml отсутствует раздел souls")

        val ids = linkedSetOf<String>()
        root?.getKeys(false)?.let(ids::addAll)
        bundledRoot?.getKeys(false)?.let(ids::addAll)

        val loaded = linkedMapOf<String, SoulDefinition>()
        for (rawId in ids) {
            val id = rawId.lowercase(Locale.ROOT)
            val section = root?.getConfigurationSection(rawId)
                ?: bundledRoot?.getConfigurationSection(rawId)
                ?: continue
            loaded[id] = parse(id, section)
        }
        require(loaded.isNotEmpty()) { "В souls.yml не настроено ни одной души" }
        definitions = loaded
        val resolved = linkedMapOf<String, SoulDefinition>()
        for (definition in loaded.values) {
            for (key in listOf(definition.id) + definition.aliases) {
                val normalized = key.lowercase(Locale.ROOT)
                val previous = resolved.putIfAbsent(normalized, definition)
                if (previous != null && previous.id != definition.id) {
                    Log.warning("Алиас души '$key' уже занят душой ${previous.id}")
                }
            }
        }
        lookup = resolved
    }

    operator fun get(id: String?): SoulDefinition? = id?.let { lookup[it.lowercase(Locale.ROOT)] }

    fun all(): Collection<SoulDefinition> = definitions.values

    fun ids(): Set<String> = definitions.keys

    fun commandNames(): List<String> = definitions.values.map { it.aliases.firstOrNull() ?: it.id }

    private fun parse(id: String, section: ConfigurationSection): SoulDefinition {
        val model = section.getConfigurationSection("model")
            ?: error("У души $id отсутствует model")
        val effects = linkedMapOf<PotionEffectType, Int>()
        section.getConfigurationSection("potion-effects")?.let { effectSection ->
            for (name in effectSection.getKeys(false)) {
                @Suppress("DEPRECATION")
                val type = PotionEffectType.getByName(name.uppercase(Locale.ROOT))
                if (type == null) {
                    Log.warning("Неизвестный эффект $name у души $id — пропущен")
                } else {
                    effects[type] = effectSection.getInt(name).coerceAtLeast(1)
                }
            }
        }

        val cropSection = section.getConfigurationSection("abilities.crop-growth")
        val dropsSection = section.getConfigurationSection("abilities.mob-drops")
        return SoulDefinition(
            id = id,
            aliases = section.getStringList("aliases").ifEmpty { defaultAliases(id) }
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter(String::isNotEmpty)
                .toCollection(linkedSetOf()),
            name = section.getString("name") ?: id,
            model = SoulModel(
                head = appearance(model.getConfigurationSection("head"), "PLAYER_HEAD"),
                chest = appearance(model.getConfigurationSection("chest"), "LEATHER_CHESTPLATE"),
                hand = appearance(model.getConfigurationSection("hand"), "STONE"),
                handPose = pose(model.getString("hand-pose")),
            ),
            trail = parseTrail(id, section.getConfigurationSection("trail")),
            lore = section.getStringList("lore"),
            potionEffects = effects,
            cropGrowth = cropSection?.takeIf { it.getBoolean("enabled", true) }
                ?.let { CropGrowthAbility(it.getInt("radius", 4).coerceIn(1, 12)) },
            mobDrops = dropsSection?.let {
                MobDropAbility(
                    multiplier = it.getDouble("multiplier", 1.0).coerceIn(1.0, 100.0),
                    chance = it.getDouble("chance", 1.0).coerceIn(0.0, 1.0),
                )
            },
            mobExperienceMultiplier = section.getDouble("abilities.mob-experience-multiplier", 1.0)
                .coerceIn(0.0, 100.0),
            infiniteFood = section.getBoolean("abilities.infinite-food", false),
        )
    }

    private fun appearance(
        section: ConfigurationSection?,
        fallbackMaterial: String,
        fallbackGlow: Boolean = false,
    ): ItemAppearance = ItemAppearance(
        material = section?.getString("material") ?: fallbackMaterial,
        customModelData = section?.takeIf { it.contains("custom-model-data") }
            ?.getInt("custom-model-data"),
        glow = section?.getBoolean("glow", fallbackGlow) ?: fallbackGlow,
        leatherColor = section?.getString("color"),
        base64 = section?.getString("base64")?.takeIf(String::isNotBlank),
    )

    private fun pose(raw: String?): ModelPose {
        val values = raw?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }.orEmpty()
        return if (values.size == 3) ModelPose(values[0], values[1], values[2])
        else ModelPose(-15.0, 0.0, -10.0)
    }

    private fun parseTrail(id: String, section: ConfigurationSection?): TrailDefinition? {
        val fallback = when (id) {
            "doubler" -> intArrayOf(255, 75, 90)
            "experienced" -> intArrayOf(255, 220, 60)
            "harvester" -> intArrayOf(90, 230, 110)
            else -> null
        }
        if (section == null && fallback == null) return null
        val color = parseRgb(section?.getString("color"), fallback ?: intArrayOf(255, 255, 255))
        return TrailDefinition(
            enabled = section?.getBoolean("enabled", true) ?: true,
            particle = section?.getString("particle", "DUST") ?: "DUST",
            red = color[0], green = color[1], blue = color[2],
            size = (section?.getDouble("size", 1.0) ?: 1.0).coerceIn(0.1, 4.0).toFloat(),
            count = (section?.getInt("count", 2) ?: 2).coerceIn(1, 20),
            spread = (section?.getDouble("spread", 0.04) ?: 0.04).coerceIn(0.0, 2.0),
            heightOffset = (section?.getDouble("height-offset", 0.10) ?: 0.10).coerceIn(-1.0, 3.0),
            backOffset = (section?.getDouble("back-offset", 0.30) ?: 0.30).coerceIn(0.0, 3.0),
            intervalTicks = (section?.getLong("interval-ticks", 2L) ?: 2L).coerceIn(1L, 100L),
        )
    }

    private fun parseRgb(raw: String?, fallback: IntArray): IntArray {
        val values = raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        return if (values.size == 3) IntArray(3) { values[it].coerceIn(0, 255) } else fallback
    }

    private fun defaultAliases(id: String): List<String> = when (id) {
        "doubler" -> listOf("дублёр", "дублер")
        "experienced" -> listOf("опытный")
        "harvester" -> listOf("урожайник")
        else -> emptyList()
    }
}
