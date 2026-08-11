package com.a0.common.engine

import com.a0.common.players.PlayerReport
import com.a0.common.players.PlayerStats
import kotlin.math.abs
import kotlin.math.sqrt

class AnticheatSession {
    // Default Game Limits
    companion object {
        const val MAX_SPEED = 10f
        const val MAX_AIM_DELTA = 180f
        const val HEADSHOT_RATIO = 0.6f
        const val WALL_HIT_THRESHOLD = 3
    }

    private val playerStats = mutableMapOf<String, PlayerStats>()
    private val playerFlags = mutableMapOf<String, MutableList<String>>()

    fun process(telemetry: TelemetryDTO) {
        val stats = playerStats.getOrPut(telemetry.playerId) { PlayerStats() }
        val flags = playerFlags.getOrPut(telemetry.playerId) { mutableListOf() }
        val dt = (telemetry.timestamp - stats.lastTimestamp).coerceAtLeast(1L)

        // Movement
        val dx = telemetry.x - stats.lastX
        val dy = telemetry.y - stats.lastY
        val dz = telemetry.z - stats.lastZ
        val speed = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat() / dt
        if (speed > MAX_SPEED) flags.add("SPEED_HACK (${speed}u/ms)")

        // Aim
        val dPitch = abs(telemetry.pitch - stats.lastPitch)
        val dYaw = abs(telemetry.yaw - stats.lastYaw)
        val aimDelta = sqrt((dPitch * dPitch + dYaw * dYaw).toDouble()).toFloat()
        if (aimDelta > MAX_AIM_DELTA) flags.add("AIM_SNAP ($aimDelta deg/frame)")

        // Combat
        if (telemetry.shotFired) stats.totalShots++
        if (telemetry.hitRegistered) stats.totalHits++
        if (telemetry.headshot) stats.totalHeadshots++
        if (telemetry.hitThroughWall) stats.wallHits++

        if (stats.totalShots > 10) {
            val headshotRatio = stats.totalHeadshots.toFloat() / stats.totalHits.coerceAtLeast(1)
            if (headshotRatio > HEADSHOT_RATIO) flags.add("HEADSHOT_RATIO ($headshotRatio)")
        }

        if (stats.wallHits >= WALL_HIT_THRESHOLD) flags.add("WALLHACK (${stats.wallHits} wall hits)")

        // Update state
        stats.lastX = telemetry.x
        stats.lastY = telemetry.y
        stats.lastZ = telemetry.z
        stats.lastPitch = telemetry.pitch
        stats.lastYaw = telemetry.yaw
        stats.lastTimestamp = telemetry.timestamp
    }

    fun shutdown(): List<PlayerReport> =
        playerFlags.map { (playerId, flags) ->
            PlayerReport(playerId, flags.toList())
        }
}
