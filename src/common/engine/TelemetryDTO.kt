package com.a0.common.engine

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryDTO(
    val playerId: String,
    val timestamp: Long,
    // Movement
    val x: Float,
    val y: Float,
    val z: Float,
    // Aim
    val pitch: Float,
    val yaw: Float,
    // Combat
    val shotFired: Boolean,
    val hitRegistered: Boolean,
    val headshot: Boolean,
    val hitThroughWall: Boolean,
)
