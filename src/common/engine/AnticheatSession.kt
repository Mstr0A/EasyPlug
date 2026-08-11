package com.a0.common.engine

import com.a0.common.players.PlayerReport
import com.a0.common.players.PlayerStats
import kotlinx.serialization.Serializable
import kotlin.math.sqrt
import kotlin.time.TimeSource

/**
 * Confusion-matrix counters for one detector, compared against a ground-truth flag.
 */
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

    fun precision(): Float =
        if (truePositives + falsePositives == 0) {
            0f
        } else {
            truePositives.toFloat() / (truePositives + falsePositives)
        }

    fun recall(): Float =
        if (truePositives + falseNegatives == 0) {
            0f
        } else {
            truePositives.toFloat() / (truePositives + falseNegatives)
        }
}

@Serializable
data class AccuracyReport(
    val playerId: String,
    val speedHackPrecision: Float,
    val speedHackRecall: Float,
    val aimbotPrecision: Float,
    val aimbotRecall: Float,
)

/**
 * Read-only live view of one player's current anticheat state, safe to poll
 * at any time without mutating or draining anything (unlike shutdown()).
 */
@Serializable
data class PlayerSnapshot(
    val playerId: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val lastSpeed: Float,
    val flagged: Boolean,
    val flags: List<String>,
    val accuracy: AccuracyReport,
)

class AnticheatSession {
    companion object {
        // Godot player SPEED = 5.0 units/sec, moving on X/Z only (gravity handles Y).
        // Telemetry is sent ~once per second (every 60 physics frames), so we allow
        // slack for float drift, jump arcs, and network jitter before flagging.
        const val MAX_SPEED = 7.5f // units/sec
        const val SPEED_HACK_STREAK_THRESHOLD = 2 // consecutive over-speed samples before flagging

        // Minimum time between samples to trust a speed calculation. Below this,
        // float timestamp noise can produce misleading spikes.
        const val MIN_DT_SECONDS = 0.05

        // A single-sample jump this large is treated as a respawn/teleport, not
        // movement, and is excluded from the speed check entirely.
        const val TELEPORT_DISTANCE = 15f // units

        // A player with no telemetry for this long is dropped from snapshot()
        // output (dashboard "clears" them) even though their old data is still
        // held internally for shutdown()/accuracyReport().
        const val STALE_AFTER_SECONDS = 5
    }

    private val playerStats = mutableMapOf<String, PlayerStats>()
    private val playerFlags = mutableMapOf<String, MutableList<String>>()
    private val speedHackStreak = mutableMapOf<String, Int>()
    private val lastSpeed = mutableMapOf<String, Float>()
    private val lastSeen = mutableMapOf<String, TimeSource.Monotonic.ValueTimeMark>()

    private val speedHackAccuracy = mutableMapOf<String, DetectorAccuracy>()
    private val aimbotAccuracy = mutableMapOf<String, DetectorAccuracy>()

    fun process(telemetry: TelemetryDTO) {
        val stats = playerStats.getOrPut(telemetry.playerId) { PlayerStats() }
        val flags = playerFlags.getOrPut(telemetry.playerId) { mutableListOf() }
        lastSeen[telemetry.playerId] = TimeSource.Monotonic.markNow()

        var speedHackDetected = false

        if (stats.hasSample) {
            val dt = telemetry.timestamp - stats.lastTimestamp
            if (dt >= MIN_DT_SECONDS) {
                val dx = telemetry.posX - stats.lastX
                val dy = telemetry.posY - stats.lastY
                val dz = telemetry.posZ - stats.lastZ
                val distance = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                val speed = distance / dt.toFloat()
                lastSpeed[telemetry.playerId] = speed

                if (distance > TELEPORT_DISTANCE) {
                    // Respawn or similar instant reposition, not movement — don't
                    // flag it, and don't let it count toward a speed-hack streak.
                    speedHackStreak[telemetry.playerId] = 0
                } else if (speed > MAX_SPEED) {
                    val streak = (speedHackStreak[telemetry.playerId] ?: 0) + 1
                    speedHackStreak[telemetry.playerId] = streak
                    if (streak >= SPEED_HACK_STREAK_THRESHOLD) {
                        speedHackDetected = true
                        flags.add("SPEED_HACK (%.2f u/s at %.3f)".format(speed, telemetry.timestamp))
                    }
                } else {
                    speedHackStreak[telemetry.playerId] = 0
                }
            }
            // dt < MIN_DT_SECONDS: skip this sample's speed check but still update lastX/Y/Z below,
            // otherwise a burst of near-simultaneous samples would compound error into the next valid dt.
        }

        // Update rolling position/time state
        stats.lastX = telemetry.posX
        stats.lastY = telemetry.posY
        stats.lastZ = telemetry.posZ
        stats.lastTimestamp = telemetry.timestamp
        stats.hasSample = true

        // Ground-truth accuracy bookkeeping (bots + demo-toggle human client both send these)
        speedHackAccuracy
            .getOrPut(telemetry.playerId) { DetectorAccuracy() }
            .record(detected = speedHackDetected, groundTruth = telemetry.groundTruthSpeedHack)

        // No aimbot detector exists yet (aim/shot fields were dropped from telemetry).
        // Recorded as "never detected" so recall reflects what's actually being caught (i.e. nothing, yet).
        aimbotAccuracy
            .getOrPut(telemetry.playerId) { DetectorAccuracy() }
            .record(detected = false, groundTruth = telemetry.groundTruthAimbot)
    }

    fun shutdown(): List<PlayerReport> = playerFlags.map { (playerId, flags) -> PlayerReport(playerId, flags.toList()) }

    /**
     * Non-destructive live view of every player's current state. Safe to call
     * repeatedly (e.g. from a polling dashboard route) at any point during a
     * running session — unlike shutdown(), nothing is drained or reset.
     */
    fun snapshot(): List<PlayerSnapshot> =
        playerStats
            .filterKeys { playerId ->
                val elapsed = lastSeen[playerId]?.elapsedNow()?.inWholeSeconds
                elapsed != null && elapsed < STALE_AFTER_SECONDS
            }.map { (playerId, stats) ->
                val speedAcc = speedHackAccuracy[playerId] ?: DetectorAccuracy()
                val aimAcc = aimbotAccuracy[playerId] ?: DetectorAccuracy()
                val flags = playerFlags[playerId] ?: emptyList()
                PlayerSnapshot(
                    playerId = playerId,
                    posX = stats.lastX,
                    posY = stats.lastY,
                    posZ = stats.lastZ,
                    lastSpeed = lastSpeed[playerId] ?: 0f,
                    flagged = flags.isNotEmpty(),
                    flags = flags.toList(),
                    accuracy =
                        AccuracyReport(
                            playerId = playerId,
                            speedHackPrecision = speedAcc.precision(),
                            speedHackRecall = speedAcc.recall(),
                            aimbotPrecision = aimAcc.precision(),
                            aimbotRecall = aimAcc.recall(),
                        ),
                )
            }

    fun accuracyReport(): List<AccuracyReport> =
        playerStats.keys.map { playerId ->
            val speedAcc = speedHackAccuracy[playerId] ?: DetectorAccuracy()
            val aimAcc = aimbotAccuracy[playerId] ?: DetectorAccuracy()
            AccuracyReport(
                playerId = playerId,
                speedHackPrecision = speedAcc.precision(),
                speedHackRecall = speedAcc.recall(),
                aimbotPrecision = aimAcc.precision(),
                aimbotRecall = aimAcc.recall(),
            )
        }
}
