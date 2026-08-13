package com.a0.common.engine

import com.a0.common.players.PlayerReport
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
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
    val lastYaw: Float,
    val lastPitch: Float,
    val angularVelocity: Float,
    val flagged: Boolean,
    val flags: Set<String>,
    val accuracy: AccuracyReport,
)

class AnticheatSession {
    companion object {
        const val MAX_LEGIT_SPEED = 7.5f
        const val SPEED_STREAK_TO_FLAG = 2
        const val MIN_DT = 0.01 // 10ms lower bound to avoid div by zero
        const val RESPAWN_HEALTH_JUMP = 25f
        const val STALE_SECONDS = 5L

        // --- Reworked Bot Aimbot Thresholds (Optimized for Slow/Deliberate Bot Movement & Slerp) ---
        const val ROTATION_WINDOW_SIZE = 5
        const val AIM_MIN_TRACKING_VEL = 1.5f // Lower velocity floor for slow-moving bot tracking
        const val AIM_MAX_TRACKING_VEL = 20.0f // Upper bound for normal slow bot rotation
        const val AIM_MAX_STD_DEV = 0.25f // Strict consistency window to catch programmatic slerp / smooth locks
        const val AIM_MIN_ANGULAR_DIST = 0.04f // Minimum angular shift to evaluate
        const val AIMBOT_STREAK_TO_FLAG = 3 // Consecutive frames required to confirm aimbot

        // Snap detection for rage/burst hard adjustments at lower speeds
        const val RAGE_SNAP_VELOCITY = 10.0f
        const val RAGE_SNAP_DIST = 0.25f
    }

    private data class RotationSample(
        val velocity: Float,
        val angularDistance: Float,
    )

    private data class State(
        // Position & Aim Baseline
        var lastX: Float = 0f,
        var lastY: Float = 0f,
        var lastZ: Float = 0f,
        var lastYaw: Float = 0f,
        var lastPitch: Float = 0f,
        var lastAngularVelocity: Float = 0f,
        var lastTimestamp: Double = 0.0,
        var lastHealth: Float = 100f,
        var lastSpeed: Float = 0f,
        var hasSample: Boolean = false,
        // Sliding window for rotation kinematics
        val rotationHistory: ArrayDeque<RotationSample> = ArrayDeque(ROTATION_WINDOW_SIZE),
        // Detection Streaks & Flags
        var speedStreak: Int = 0,
        var aimbotStreak: Int = 0,
        val flags: MutableSet<String> = mutableSetOf(),
        // Accuracy
        val speedAccuracy: DetectorAccuracy = DetectorAccuracy(),
        val aimbotAccuracy: DetectorAccuracy = DetectorAccuracy(),
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
        var aimbotDetected = false

        if (state.hasSample) {
            val isRespawn = telemetry.isRespawn || ((telemetry.health - state.lastHealth) >= RESPAWN_HEALTH_JUMP)
            val dt = telemetry.timestamp - state.lastTimestamp

            if (isRespawn) {
                state.speedStreak = 0
                state.aimbotStreak = 0
                state.lastSpeed = 0f
                state.lastAngularVelocity = 0f
                state.rotationHistory.clear()
                updateBaseline(state, telemetry)
                return
            }

            if (dt >= MIN_DT) {
                val dtFloat = dt.toFloat()

                // -------------------------------------------------------------
                // 1. SPEED EVALUATION (Planar 2D Check)
                // -------------------------------------------------------------
                val dx = telemetry.posX - state.lastX
                val dz = telemetry.posZ - state.lastZ
                val planarDistance = sqrt((dx * dx + dz * dz).toDouble()).toFloat()
                val speed = planarDistance / dtFloat
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

                // -------------------------------------------------------------
                // 2. REWORKED BOT AIMBOT EVALUATION (Slerp & Lock Detection)
                // -------------------------------------------------------------
                val dYaw = getShortestAngleDifference(state.lastYaw, telemetry.lookYaw)
                val dPitch = telemetry.lookPitch - state.lastPitch
                val angularDistance = sqrt((dYaw * dYaw + dPitch * dPitch).toDouble()).toFloat()
                val currentAngularVel = angularDistance / dtFloat
                state.lastAngularVelocity = currentAngularVel

                // Update sliding window
                if (state.rotationHistory.size >= ROTATION_WINDOW_SIZE) {
                    state.rotationHistory.removeFirst()
                }
                state.rotationHistory.addLast(RotationSample(currentAngularVel, angularDistance))

                var isAimingAnomaly = false

                if (angularDistance >= AIM_MIN_ANGULAR_DIST && state.rotationHistory.size >= ROTATION_WINDOW_SIZE) {
                    val velocities = state.rotationHistory.map { p -> p.velocity }
                    val avgVel = velocities.average().toFloat()

                    // Check if average rotational velocity matches slow bot tracking range
                    if (avgVel in AIM_MIN_TRACKING_VEL..AIM_MAX_TRACKING_VEL) {
                        val variance = velocities.map { v -> (v - avgVel) * (v - avgVel) }.average().toFloat()
                        val stdDev = sqrt(variance)

                        // Low standard deviation indicates algorithmic locking / slerp stability
                        if (stdDev < AIM_MAX_STD_DEV) {
                            isAimingAnomaly = true
                        }
                    }

                    // Check for sharp programmatic snap / lock adjustments at lower speeds
                    if (currentAngularVel > RAGE_SNAP_VELOCITY && angularDistance > RAGE_SNAP_DIST) {
                        isAimingAnomaly = true
                    }
                }

                // Decision Engine with Streak Confirmation
                if (isAimingAnomaly) {
                    state.aimbotStreak++
                    if (state.aimbotStreak >= AIMBOT_STREAK_TO_FLAG) {
                        aimbotDetected = true
                        state.flags.add("AIMBOT")
                    }
                } else {
                    state.aimbotStreak = maxOf(0, state.aimbotStreak - 1)
                }

                updateBaseline(state, telemetry)
            }
        } else {
            updateBaseline(state, telemetry)
            state.hasSample = true
        }

        state.speedAccuracy.record(detected = speedHackDetected, groundTruth = telemetry.groundTruthSpeedHack)
        state.aimbotAccuracy.record(detected = aimbotDetected, groundTruth = telemetry.groundTruthAimbot)
    }

    private fun updateBaseline(
        state: State,
        telemetry: TelemetryDTO,
    ) {
        state.lastX = telemetry.posX
        state.lastY = telemetry.posY
        state.lastZ = telemetry.posZ
        state.lastYaw = telemetry.lookYaw
        state.lastPitch = telemetry.lookPitch
        state.lastTimestamp = telemetry.timestamp
        state.lastHealth = telemetry.health
    }

    private fun getShortestAngleDifference(
        angle1: Float,
        angle2: Float,
    ): Float {
        val pi = PI.toFloat()
        val twoPi = 2f * pi
        var diff = (angle2 - angle1) % twoPi
        if (diff > pi) diff -= twoPi
        if (diff < -pi) diff += twoPi
        return abs(diff)
    }

    private fun buildAccuracyReport(
        playerId: String,
        state: State,
    ) = AccuracyReport(
        playerId = playerId,
        speedHackPrecision = state.speedAccuracy.precision(),
        speedHackRecall = state.speedAccuracy.recall(),
        aimbotPrecision = state.aimbotAccuracy.precision(),
        aimbotRecall = state.aimbotAccuracy.recall(),
    )

    fun globalPerformanceReport(): GlobalPerformanceReport {
        var totalTP_Speed = 0
        var totalFP_Speed = 0
        var totalTN_Speed = 0
        var totalFN_Speed = 0

        var totalTP_Aim = 0
        var totalFP_Aim = 0
        var totalTN_Aim = 0
        var totalFN_Aim = 0

        for ((_, state) in players) {
            totalTP_Speed += state.speedAccuracy.truePositives
            totalFP_Speed += state.speedAccuracy.falsePositives
            totalTN_Speed += state.speedAccuracy.trueNegatives
            totalFN_Speed += state.speedAccuracy.falseNegatives

            totalTP_Aim += state.aimbotAccuracy.truePositives
            totalFP_Aim += state.aimbotAccuracy.falsePositives
            totalTN_Aim += state.aimbotAccuracy.trueNegatives
            totalFN_Aim += state.aimbotAccuracy.falseNegatives
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
            aimbotMetrics = calculateSummary(totalTP_Aim, totalFP_Aim, totalTN_Aim, totalFN_Aim),
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
                    lastYaw = state.lastYaw,
                    lastPitch = state.lastPitch,
                    angularVelocity = state.lastAngularVelocity,
                    flagged = state.flags.isNotEmpty(),
                    flags = state.flags.toSet(),
                    accuracy = buildAccuracyReport(id, state),
                )
            }

    fun shutdown(): List<PlayerReport> = players.map { (id, state) -> PlayerReport(id, state.flags.toList()) }

    fun accuracyReport(): List<AccuracyReport> = players.map { (id, state) -> buildAccuracyReport(id, state) }
}
