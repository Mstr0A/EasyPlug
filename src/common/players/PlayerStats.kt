package com.a0.common.players

data class PlayerStats(
    var lastX: Float = 0f,
    var lastY: Float = 0f,
    var lastZ: Float = 0f,
    var lastTimestamp: Double = 0.0,
    var hasSample: Boolean = false,
)

data class PlayerReport(
    val playerId: String,
    val flags: List<String>,
    val flagged: Boolean = flags.isNotEmpty(),
)
