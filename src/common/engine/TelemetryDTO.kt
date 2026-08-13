package com.a0.common.engine

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryDTO(
    val playerId: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val cheaterProfile: String? = null,
    val groundTruthSpeedHack: Boolean = false,
    val isRespawn: Boolean = false,
    val health: Float = 100f,
    val timestamp: Double,
)

@Serializable
data class AccuracyReport(
    val playerId: String,
    val speedHackPrecision: Float,
    val speedHackRecall: Float,
)

@Serializable
data class GlobalPerformanceReport(
    val totalPlayersTracked: Int,
    val speedHackMetrics: DetectorMetricSummary,
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
