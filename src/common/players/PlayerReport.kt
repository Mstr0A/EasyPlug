package com.a0.common.players

data class PlayerReport(
    val playerId: String,
    val flags: List<String>,
    val flagged: Boolean = flags.isNotEmpty(),
)
