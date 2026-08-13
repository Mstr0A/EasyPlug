@file:Suppress("ktlint:standard:property-naming")

package com.a0.common.engine

import com.a0.common.players.PlayerReport
import kotlinx.serialization.Serializable
import kotlin.math.sqrt
import kotlin.time.TimeSource

private data class DetectorAccuracy(
    var truePositives: Int = 0,
    var falsePositives: Int = 0,
    var trueNegatives: Int = 0,
    var falseNegatives: Int = 0,
) {
    fun record(
        detected: Boolean,
        groundTruth: Boolean,
    ) {
        when {
            detected && groundTruth -> truePositives++
            detected && !groundTruth -> falsePositives++
            !detected && groundTruth -> falseNegatives++
            else -> trueNegatives++
        }
    }

    fun precision() =
        if (truePositives + falsePositives == 0) {
            1f
        } else {
            truePositives.toFloat() / (truePositives + falsePositives)
        }

    fun recall() =
        if (truePositives + falseNegatives == 0) {
            1f
        } else {
            truePositives.toFloat() / (truePositives + falseNegatives)
        }
}

@Serializable
data class PlayerSnapshot(
    val playerId: String,
    val cheaterProfile: String?,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val lastSpeed: Float,
    val flagged: Boolean,
    val flags: Set<String>,
    val accuracy: AccuracyReport,
)

class AnticheatSession {
    companion object {
        const val MAX_LEGIT_SPEED = 7f
        const val SPEED_STREAK_TO_FLAG = 1
        const val MIN_DT = 0.001 // Reduced to 1ms to prevent dropping high-frequency localhost ticks
        const val RESPAWN_HEALTH_JUMP = 25f
        const val STALE_SECONDS = 5L
    }

    private data class State(
        // Position Baseline
        var lastX: Float = 0f,
        var lastY: Float = 0f,
        var lastZ: Float = 0f,
        var lastTimestamp: Double = 0.0,
        var lastHealth: Float = 100f,
        var lastSpeed: Float = 0f,
        var hasSample: Boolean = false,
        // Detection Streaks & Flags
        var speedStreak: Int = 0,
        val flags: MutableSet<String> = mutableSetOf(),
        // Accuracy
        val speedAccuracy: DetectorAccuracy = DetectorAccuracy(),
        // Meta
        var cheaterProfile: String? = null,
        var lastSeen: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow(),
    )

    private val players = mutableMapOf<String, State>()

    fun process(telemetry: TelemetryDTO) {
        val state = players.getOrPut(telemetry.playerId) { State() }
        state.lastSeen = TimeSource.Monotonic.markNow()
        state.cheaterProfile = telemetry.cheaterProfile

        var speedHackDetected = false

        if (state.hasSample) {
            val isRespawn = telemetry.isRespawn || ((telemetry.health - state.lastHealth) >= RESPAWN_HEALTH_JUMP)
            val dt = telemetry.timestamp - state.lastTimestamp

            if (isRespawn) {
                state.speedStreak = 0
                state.lastSpeed = 0f
                updateBaseline(state, telemetry)
                return
            }

            if (dt >= MIN_DT) {
                val dtFloat = dt.toFloat()

                // -------------------------------------------------------------
                // SPEED EVALUATION (Full 3D Euclidean Vector Check)
                // -------------------------------------------------------------
                val dx = telemetry.posX - state.lastX
                val dy = telemetry.posY - state.lastY
                val dz = telemetry.posZ - state.lastZ
                val totalDistance = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                val speed = totalDistance / dtFloat
                state.lastSpeed = speed

                if (speed > MAX_LEGIT_SPEED) {
                    state.speedStreak++
                    if (state.speedStreak >= SPEED_STREAK_TO_FLAG) {
                        speedHackDetected = true
                        state.flags.add("SPEED_HACK")
                    }
                } else {
                    state.speedStreak = maxOf(0, state.speedStreak - 1)
                }

                updateBaseline(state, telemetry)
            }
        } else {
            updateBaseline(state, telemetry)
            state.hasSample = true
        }

        state.speedAccuracy.record(detected = speedHackDetected, groundTruth = telemetry.groundTruthSpeedHack)
    }

    private fun updateBaseline(
        state: State,
        telemetry: TelemetryDTO,
    ) {
        state.lastX = telemetry.posX
        state.lastY = telemetry.posY
        state.lastZ = telemetry.posZ
        state.lastTimestamp = telemetry.timestamp
        state.lastHealth = telemetry.health
    }

    private fun buildAccuracyReport(
        playerId: String,
        state: State,
    ) = AccuracyReport(
        playerId = playerId,
        speedHackPrecision = state.speedAccuracy.precision(),
        speedHackRecall = state.speedAccuracy.recall(),
    )

    fun globalPerformanceReport(): GlobalPerformanceReport {
        var totalTP_Speed = 0
        var totalFP_Speed = 0
        var totalTN_Speed = 0
        var totalFN_Speed = 0

        for ((_, state) in players) {
            totalTP_Speed += state.speedAccuracy.truePositives
            totalFP_Speed += state.speedAccuracy.falsePositives
            totalTN_Speed += state.speedAccuracy.trueNegatives
            totalFN_Speed += state.speedAccuracy.falseNegatives
        }

        fun calculateSummary(
            tp: Int,
            fp: Int,
            tn: Int,
            fn: Int,
        ): DetectorMetricSummary {
            val precision = if (tp + fp == 0) 1f else tp.toFloat() / (tp + fp)
            val recall = if (tp + fn == 0) 1f else tp.toFloat() / (tp + fn)
            val total = tp + fp + tn + fn
            val accuracy = if (total == 0) 1f else (tp + tn).toFloat() / total

            return DetectorMetricSummary(
                truePositives = tp,
                falsePositives = fp,
                trueNegatives = tn,
                falseNegatives = fn,
                precision = precision,
                recall = recall,
                accuracy = accuracy,
            )
        }

        return GlobalPerformanceReport(
            totalPlayersTracked = players.size,
            speedHackMetrics = calculateSummary(totalTP_Speed, totalFP_Speed, totalTN_Speed, totalFN_Speed),
        )
    }

    fun snapshot(): List<PlayerSnapshot> =
        players
            .filterValues { it.lastSeen.elapsedNow().inWholeSeconds < STALE_SECONDS }
            .map { (id, state) ->
                PlayerSnapshot(
                    playerId = id,
                    cheaterProfile = state.cheaterProfile,
                    posX = state.lastX,
                    posY = state.lastY,
                    posZ = state.lastZ,
                    lastSpeed = state.lastSpeed,
                    flagged = state.flags.isNotEmpty(),
                    flags = state.flags.toSet(),
                    accuracy = buildAccuracyReport(id, state),
                )
            }

    fun shutdown(): List<PlayerReport> = players.map { (id, state) -> PlayerReport(id, state.flags.toList()) }

    fun accuracyReport(): List<AccuracyReport> = players.map { (id, state) -> buildAccuracyReport(id, state) }
}
