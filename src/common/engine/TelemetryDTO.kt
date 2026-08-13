package com.a0.common.engine

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryDTO(
    val playerId: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val lookYaw: Float = 0f,
    val lookPitch: Float = 0f,
    val cheaterProfile: String? = null,
    val groundTruthSpeedHack: Boolean = false,
    val groundTruthAimbot: Boolean = false,
    val isRespawn: Boolean = false,
    val health: Float = 100f,
    val timestamp: Double,
)

@Serializable
data class AccuracyReport(
    val playerId: String,
    val speedHackPrecision: Float,
    val speedHackRecall: Float,
    val aimbotPrecision: Float,
    val aimbotRecall: Float,
)

@Serializable
data class GlobalPerformanceReport(
    val totalPlayersTracked: Int,
    val speedHackMetrics: DetectorMetricSummary,
    val aimbotMetrics: DetectorMetricSummary,
)

@Serializable
data class DetectorMetricSummary(
    val truePositives: Int,
    val falsePositives: Int,
    val trueNegatives: Int,
    val falseNegatives: Int,
    val precision: Float,
    val recall: Float,
    val accuracy: Float,
)
