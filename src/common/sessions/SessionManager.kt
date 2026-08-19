package com.a0.common.sessions

import com.a0.common.players.Player
import com.a0.common.players.PlayerReport
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// Session management for actual games (unused in demo)
object SessionManager {
    // Session variables
    private val _newSessions: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val newSessions: Set<UUID> get() = _newSessions

    private val _registeredSessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()
    val registeredSessions: Map<UUID, Session> get() = _registeredSessions

    private val _liveSessions: ConcurrentHashMap<UUID, LiveSession> = ConcurrentHashMap()
    val liveSessions: Map<UUID, LiveSession> get() = _liveSessions

    private var _lastReport: List<PlayerReport> = emptyList()
    val lastReport: List<PlayerReport> get() = _lastReport

    fun createNewSession(): UUID {
        val newSessionUUID = UUID.randomUUID()
        _newSessions.add(newSessionUUID)

        return newSessionUUID
    }

    fun cancelSession(sessionUUID: UUID) {
        _newSessions.remove(sessionUUID)
        _registeredSessions.remove(sessionUUID)
    }

    fun registerSession(
        sessionUUID: UUID,
        playerList: List<Player>,
    ) {
        val sessionToRegister = Session(sessionUUID, playerList)

        _newSessions.remove(sessionUUID)

        // Register the session
        _registeredSessions[sessionUUID] = sessionToRegister
    }

    fun startSession(sessionUUID: UUID): LiveSession {
        // Remove it so it's now live
        // We're sure it's not null because it checks in the API call before it gets here
        val sessionToStart = _registeredSessions.remove(sessionUUID)!!

        val liveSession = LiveSession(sessionToStart)

        _liveSessions[sessionUUID] = liveSession

        return liveSession
    }

    fun endSession(sessionUUID: UUID): List<PlayerReport> {
        // It's a running session so it's not null
        val sessionToEnd = _liveSessions.remove(sessionUUID)!!

        val endResults = sessionToEnd.anticheat.shutdown()

        return endResults
    }
}
