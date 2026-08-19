package com.a0.common.players

// A basic player report
data class PlayerReport(
    val playerId: String,
    val flags: List<String>,
    val flagged: Boolean = flags.isNotEmpty(),
)
