package com.a0.common.engine

sealed class JudgementResult {
    object Clean : JudgementResult()

    data class Flagged(
        val playerId: String,
        val flags: List<String>,
    ) : JudgementResult()
}
