package com.a0.common.sessions

import com.a0.common.players.Player
import java.util.*

data class Session(
    val sessionID: UUID,
    val players: List<Player>,
)
