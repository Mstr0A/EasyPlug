package com.a0.common.sessions

import com.a0.common.players.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionManager {
    // Session variables
    private val newSessions: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val registeredSessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()
    private val liveSessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()

    fun createNewSession(): UUID {
        val newSessionUUID = UUID.randomUUID()
        newSessions.add(newSessionUUID)

        return newSessionUUID
    }

    fun registerSession(
        sessionUUID: UUID,
        playerList: List<Player>,
    ) {
        val sessionToRegister = Session(sessionUUID, playerList)

        newSessions.remove(sessionUUID)

        // Register the session
        registeredSessions[sessionUUID] = sessionToRegister
    }
}
