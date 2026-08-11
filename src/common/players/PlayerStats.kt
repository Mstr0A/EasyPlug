package com.a0.common.players

data class PlayerStats(
    var lastX: Float = 0f,
    var lastY: Float = 0f,
    var lastZ: Float = 0f,
    var lastPitch: Float = 0f,
    var lastYaw: Float = 0f,
    var lastTimestamp: Long = 0L,
    var totalShots: Int = 0,
    var totalHits: Int = 0,
    var totalHeadshots: Int = 0,
    var wallHits: Int = 0,
)

data class PlayerReport(
    val playerId: String,
    val flags: List<String>,
    val flagged: Boolean = flags.isNotEmpty(),
)
