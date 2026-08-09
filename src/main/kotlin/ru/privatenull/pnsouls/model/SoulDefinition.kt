package ru.privatenull.pnsouls.model

import org.bukkit.potion.PotionEffectType

data class SoulDefinition(
    val id: String,
    val aliases: Set<String>,
    val name: String,
    val model: SoulModel,
    val trail: TrailDefinition?,
    val lore: List<String>,
    val potionEffects: Map<PotionEffectType, Int>,
    val cropGrowth: CropGrowthAbility?,
    val mobDrops: MobDropAbility?,
    val mobExperienceMultiplier: Double,
    val infiniteFood: Boolean,
)

data class ItemAppearance(
    val material: String,
    val customModelData: Int?,
    val glow: Boolean = false,
    val leatherColor: String? = null,
    val base64: String? = null,
)

data class SoulModel(
    val head: ItemAppearance,
    val chest: ItemAppearance,
    val hand: ItemAppearance,
    val handPose: ModelPose,
)

data class ModelPose(val x: Double, val y: Double, val z: Double)

data class TrailDefinition(
    val enabled: Boolean,
    val particle: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val size: Float,
    val count: Int,
    val spread: Double,
    val heightOffset: Double,
    val backOffset: Double,
    val intervalTicks: Long,
)

data class CropGrowthAbility(val radius: Int)

data class MobDropAbility(val multiplier: Double, val chance: Double)
