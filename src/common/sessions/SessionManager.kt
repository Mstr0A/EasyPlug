package com.a0.common.sessions

import com.a0.common.players.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionManager {
    // Session variables
    private val _newSessions: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val newSessions: Set<UUID> get() = _newSessions

    private val _registeredSessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()
    val registeredSessions: Map<UUID, Session> get() = _registeredSessions

    private val _liveSessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()
    val liveSessions: Map<UUID, Session> get() = _liveSessions

    fun createNewSession(): UUID {
        val newSessionUUID = UUID.randomUUID()
        _newSessions.add(newSessionUUID)

        return newSessionUUID
    }

    fun cancelSession(sessionUUID: UUID) {
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
}
