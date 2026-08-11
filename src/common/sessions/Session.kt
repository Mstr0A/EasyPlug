package com.a0.common.sessions

import com.a0.common.engine.AnticheatSession
import com.a0.common.players.Player
import java.util.*

data class Session(
    val sessionId: UUID,
    val players: List<Player>,
)

data class LiveSession(
    val session: Session,
    val anticheat: AnticheatSession = AnticheatSession(),
)
