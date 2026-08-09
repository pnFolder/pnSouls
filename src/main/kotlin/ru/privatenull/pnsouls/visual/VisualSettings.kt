package ru.privatenull.pnsouls.visual

import org.bukkit.configuration.file.FileConfiguration

data class VisualSettings(
    val updatePeriod: Long,
    val rightOffset: Double,
    val backOffset: Double,
    val heightOffset: Double,
    val bobAmplitude: Double,
    val bobPeriod: Double,
    val followStrength: Double,
    val velocityDamping: Double,
    val verticalFollowStrength: Double,
    val verticalVelocityDamping: Double,
    val maxVerticalSpeed: Double,
    val rotationFollowStrength: Double,
    val maxRotationSpeedDegrees: Float,
    val ownerScreenProtection: Boolean,
    val ownerHideAngleDegrees: Double,
    val ownerShowAngleDegrees: Double,
    val ownerHideDistance: Double,
    val ownerHideDelayTicks: Int,
    val ownerShowDelayTicks: Int,
    val predictionTicks: Double,
    val snapDistance: Double,
    val small: Boolean,
) {
    companion object {
        fun from(config: FileConfiguration) = VisualSettings(
            updatePeriod = config.getLong("visual.update-period-ticks", 1L).coerceAtLeast(1L),
            rightOffset = config.getDouble("visual.right-offset", 1.2),
            backOffset = config.getDouble("visual.back-offset", 0.75).coerceIn(0.0, 4.0),
            heightOffset = config.getDouble("visual.height-offset", 0.35),
            bobAmplitude = config.getDouble("visual.bob-amplitude", 0.1).coerceAtLeast(0.0),
            bobPeriod = config.getDouble("visual.bob-period-ticks", 40.0).coerceAtLeast(1.0),
            followStrength = config.getDouble("visual.follow-strength", 0.32).coerceIn(0.01, 1.0),
            velocityDamping = config.getDouble("visual.velocity-damping", 0.68).coerceIn(0.0, 0.99),
            verticalFollowStrength = config.getDouble("visual.vertical-follow-strength", 0.10).coerceIn(0.01, 1.0),
            verticalVelocityDamping = config.getDouble("visual.vertical-velocity-damping", 0.78).coerceIn(0.0, 0.99),
            maxVerticalSpeed = config.getDouble("visual.max-vertical-speed", 0.18).coerceIn(0.03, 1.0),
            rotationFollowStrength = config.getDouble("visual.rotation-follow-strength", 0.12).coerceIn(0.01, 1.0),
            maxRotationSpeedDegrees = config.getDouble("visual.max-rotation-speed-degrees", 8.0)
                .coerceIn(1.0, 90.0).toFloat(),
            ownerScreenProtection = config.getBoolean("visual.owner-screen-protection", true),
            ownerHideAngleDegrees = config.getDouble("visual.owner-hide-angle-degrees", 70.0)
                .coerceIn(10.0, 120.0),
            ownerShowAngleDegrees = config.getDouble("visual.owner-show-angle-degrees", 82.0)
                .coerceIn(10.0, 150.0),
            ownerHideDistance = config.getDouble("visual.owner-hide-distance", 3.5).coerceIn(0.5, 12.0),
            ownerHideDelayTicks = config.getInt("visual.owner-hide-delay-ticks", 12).coerceIn(1, 200),
            ownerShowDelayTicks = config.getInt("visual.owner-show-delay-ticks", 4).coerceIn(1, 200),
            predictionTicks = config.getDouble("visual.prediction-ticks", 1.5).coerceIn(0.0, 10.0),
            snapDistance = config.getDouble("visual.snap-distance", 8.0).coerceAtLeast(1.0),
            small = config.getBoolean("visual.small", true),
        )
    }
}
