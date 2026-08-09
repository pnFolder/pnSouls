package ru.privatenull.pnsouls.visual

import org.bukkit.Color
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.EntityEquipment
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import ru.privatenull.pnsouls.item.SoulItemFactory
import ru.privatenull.pnsouls.model.SoulDefinition
import ru.privatenull.pnsouls.model.TrailDefinition
import java.util.UUID
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class SoulVisual(
    private val plugin: JavaPlugin,
    private val player: Player,
    private val definition: SoulDefinition,
    private val items: SoulItemFactory,
    private val settings: VisualSettings,
) {
    private var stand: ArmorStand? = null
    private var position: Vector? = null
    private var velocity = Vector()
    private var yaw = player.location.yaw
    private var orbitYaw = player.location.yaw
    private val phase = (player.uniqueId.hashCode() and 1023) / 1023.0 * PI * 2.0
    private val trail = definition.trail?.takeIf(TrailDefinition::enabled)
    private val trailParticle = trail?.let { resolveParticle(it.particle) }
    private val dust = trail?.let { Particle.DustOptions(Color.fromRGB(it.red, it.green, it.blue), it.size) }
    private var hiddenFromOwner = false
    private var ownerInsideScreenTicks = 0
    private var ownerOutsideScreenTicks = 0
    private val hiddenFromViewers = mutableSetOf<UUID>()

    val ownerId: UUID get() = player.uniqueId

    fun spawn(tick: Long) {
        remove()
        val target = targetLocation(tick)
        position = target.toVector()
        velocity.zero()
        yaw = player.location.yaw
        orbitYaw = player.location.yaw
        stand = player.world.spawn(target, ArmorStand::class.java).apply {
            isVisible = false
            setGravity(false)
            isSmall = settings.small
            setArms(true)
            setBasePlate(false)
            isMarker = true
            isInvulnerable = true
            isSilent = true
            isCustomNameVisible = false
            addScoreboardTag(VisualManager.VISUAL_TAG)
            rightArmPose = definition.model.handPose.let {
                EulerAngle(Math.toRadians(it.x), Math.toRadians(it.y), Math.toRadians(it.z))
            }
            equipment?.equip()
        }
        hiddenFromOwner = false
        ownerInsideScreenTicks = 0
        ownerOutsideScreenTicks = 0
        hiddenFromViewers.clear()
        stand?.let {
            updateOwnerVisibility(it)
            updateExternalVisibility(it)
        }
    }

    fun tick(tick: Long) {
        if (!player.isOnline || player.isDead) return
        val currentStand = stand
        if (currentStand == null || !currentStand.isValid || currentStand.world.uid != player.world.uid) {
            spawn(tick)
            return
        }

        orbitYaw = smoothAngleLimited(
            orbitYaw,
            player.location.yaw,
            settings.rotationFollowStrength.toFloat(),
            settings.maxRotationSpeedDegrees,
        )
        val target = targetLocation(tick)
        val current = position ?: target.toVector()
        val error = target.toVector().subtract(current)
        if (error.lengthSquared() > settings.snapDistance * settings.snapDistance) {
            current.copy(target.toVector())
            velocity.zero()
        } else {
            velocity.x = velocity.x * settings.velocityDamping + error.x * settings.followStrength
            velocity.z = velocity.z * settings.velocityDamping + error.z * settings.followStrength
            velocity.y = (velocity.y * settings.verticalVelocityDamping +
                error.y * settings.verticalFollowStrength).coerceIn(
                -settings.maxVerticalSpeed,
                settings.maxVerticalSpeed,
            )
            val horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z
            if (horizontalSpeedSquared > 2.25) {
                val scale = 1.5 / kotlin.math.sqrt(horizontalSpeedSquared)
                velocity.x *= scale
                velocity.z *= scale
            }
            current.add(velocity)
        }
        position = current
        yaw = smoothAngle(yaw, player.location.yaw, 0.30f)
        currentStand.teleport(Location(player.world, current.x, current.y, current.z, yaw, 0f))
        updateOwnerVisibility(currentStand)
        updateExternalVisibility(currentStand)
        emitTrail(currentStand, tick)
    }

    fun remove() {
        if (hiddenFromOwner) {
            stand?.takeIf { it.isValid }?.let(::showForOwner)
        }
        stand?.takeIf { it.isValid }?.let { currentStand ->
            hiddenFromViewers.mapNotNull(Bukkit::getPlayer).forEach { viewer ->
                changeViewerVisibility(viewer, "showEntity", currentStand)
            }
        }
        stand?.takeIf { it.isValid }?.remove()
        stand = null
        hiddenFromOwner = false
        ownerInsideScreenTicks = 0
        ownerOutsideScreenTicks = 0
        hiddenFromViewers.clear()
    }

    private fun EntityEquipment.equip() {
        helmet = items.createModelItem(definition.model.head)
        chestplate = items.createModelItem(definition.model.chest)
        setItemInMainHand(items.createModelItem(definition.model.hand))
    }

    private fun targetLocation(tick: Long): Location {
        val base = player.location
        val radians = Math.toRadians(orbitYaw.toDouble())
        val right = Vector(-cos(radians), 0.0, -sin(radians)).multiply(settings.rightOffset)
        val back = Vector(sin(radians), 0.0, -cos(radians)).multiply(settings.backOffset)
        // Horizontal prediction reduces trailing while running. Predicting Y would make the
        // soul jump before the spring can smooth the player's sudden vertical velocity.
        val predicted = player.velocity.clone().setY(0.0).multiply(settings.predictionTicks)
        val bob = sin((tick / settings.bobPeriod) * PI * 2.0 + phase) * settings.bobAmplitude
        return base.clone().add(right).add(back).add(predicted)
            .add(0.0, BASE_HEIGHT_ABOVE_PLAYER + settings.heightOffset + bob, 0.0)
    }

    private fun smoothAngle(current: Float, target: Float, factor: Float): Float {
        var delta = (target - current) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return current + delta * factor
    }

    private fun smoothAngleLimited(current: Float, target: Float, factor: Float, maxStep: Float): Float {
        var delta = (target - current) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return current + (delta * factor).coerceIn(-maxStep, maxStep)
    }

    private fun emitTrail(currentStand: ArmorStand, tick: Long) {
        // The trail must not remain in the owner's view after the soul itself was hidden.
        if (hiddenFromOwner) return
        val settings = trail ?: return
        val particle = trailParticle ?: return
        if (tick % settings.intervalTicks != 0L || velocity.lengthSquared() < 0.0005) return
        val location = currentStand.location.add(0.0, settings.heightOffset, 0.0)
        val backwards = velocity.clone().normalize().multiply(-settings.backOffset)
        location.add(backwards)
        try {
            if (particle.dataType == Particle.DustOptions::class.java) {
                player.world.spawnParticle(
                    particle, location, settings.count,
                    settings.spread, settings.spread, settings.spread, 0.0, dust,
                )
            } else if (particle.dataType == Void::class.java) {
                player.world.spawnParticle(
                    particle, location, settings.count,
                    settings.spread, settings.spread, settings.spread, 0.0,
                )
            }
        } catch (_: RuntimeException) {
            // Some version-specific particles require block/item data; unsupported types stay silent.
        }
    }

    private fun updateOwnerVisibility(currentStand: ArmorStand) {
        if (!settings.ownerScreenProtection) {
            if (hiddenFromOwner) {
                showForOwner(currentStand)
                hiddenFromOwner = false
            }
            ownerInsideScreenTicks = 0
            ownerOutsideScreenTicks = 0
            return
        }

        val visualCenter = currentStand.location.add(0.0, if (currentStand.isSmall) 0.85 else 1.45, 0.0)
        val toSoul = visualCenter.toVector().subtract(player.eyeLocation.toVector())
        val distanceSquared = toSoul.lengthSquared()
        if (distanceSquared < 0.0001) return
        val dot = player.eyeLocation.direction.normalize().dot(toSoul.normalize())
        val hideThreshold = cos(Math.toRadians(settings.ownerHideAngleDegrees))
        val showThreshold = cos(Math.toRadians(settings.ownerShowAngleDegrees))
        val maximumDistanceSquared = settings.ownerHideDistance * settings.ownerHideDistance

        if (!hiddenFromOwner) {
            ownerOutsideScreenTicks = 0
            if (distanceSquared <= maximumDistanceSquared && dot >= hideThreshold) {
                ownerInsideScreenTicks++
                if (ownerInsideScreenTicks >= settings.ownerHideDelayTicks) {
                    hiddenFromOwner = hideFromOwner(currentStand)
                    ownerInsideScreenTicks = 0
                }
            } else {
                ownerInsideScreenTicks = 0
            }
        } else {
            ownerInsideScreenTicks = 0
            if (distanceSquared > maximumDistanceSquared || dot <= showThreshold) {
                ownerOutsideScreenTicks++
                if (ownerOutsideScreenTicks >= settings.ownerShowDelayTicks) {
                    showForOwner(currentStand)
                    hiddenFromOwner = false
                    ownerOutsideScreenTicks = 0
                }
            } else {
                ownerOutsideScreenTicks = 0
            }
        }
    }

    /**
     * Player#hideEntity/showEntity are intentionally called reflectively: pnSouls still
     * compiles against Paper 1.16.5, while the owner's camera protection is available on
     * modern servers (including the target 1.21.4 server).
     */
    private fun hideFromOwner(currentStand: ArmorStand): Boolean =
        changeViewerVisibility(player, "hideEntity", currentStand)

    private fun showForOwner(currentStand: ArmorStand): Boolean =
        changeViewerVisibility(player, "showEntity", currentStand)

    private fun updateExternalVisibility(currentStand: ArmorStand) {
        val globallyHidden = player.gameMode == GameMode.SPECTATOR ||
            player.hasMetadata("vanished") || player.hasMetadata("vanish")

        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer.uniqueId == player.uniqueId) return@forEach
            val shouldHide = globallyHidden || !viewer.canSee(player)
            val isHidden = hiddenFromViewers.contains(viewer.uniqueId)
            if (shouldHide && !isHidden) {
                if (changeViewerVisibility(viewer, "hideEntity", currentStand)) {
                    hiddenFromViewers.add(viewer.uniqueId)
                }
            } else if (!shouldHide && isHidden) {
                changeViewerVisibility(viewer, "showEntity", currentStand)
                hiddenFromViewers.remove(viewer.uniqueId)
            }
        }
        hiddenFromViewers.removeIf { Bukkit.getPlayer(it) == null }
    }

    private fun changeViewerVisibility(
        viewer: Player,
        methodName: String,
        currentStand: ArmorStand,
    ): Boolean {
        val method = viewer.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 2
        } ?: return false
        return runCatching {
            method.invoke(viewer, plugin, currentStand)
            true
        }.getOrDefault(false)
    }

    private fun resolveParticle(raw: String): Particle? {
        val normalized = raw.trim().uppercase(Locale.ROOT)
        val names = when (normalized) {
            "DUST" -> listOf("DUST", "REDSTONE")
            "REDSTONE" -> listOf("REDSTONE", "DUST")
            else -> listOf(normalized)
        }
        return names.firstNotNullOfOrNull { name ->
            runCatching { Particle.valueOf(name) }.getOrNull()
        }
    }

    companion object {
        private const val BASE_HEIGHT_ABOVE_PLAYER = 0.90
    }

}
