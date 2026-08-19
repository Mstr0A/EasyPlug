package com.a0.common.sessions

import com.a0.common.engine.AnticheatSession
import com.a0.common.players.Player
import java.util.*

// Session meta-data
data class Session(
    val sessionId: UUID,
    val players: List<Player>,
)

// Live session meta-data
data class LiveSession(
    val session: Session,
    val anticheat: AnticheatSession = AnticheatSession(),
)
