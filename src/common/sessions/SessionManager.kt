package com.a0.common.sessions

import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SessionManager {
    val newSessions: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val registeredSessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()
    val liveSessions: ConcurrentHashMap<UUID, Session> = ConcurrentHashMap()
}
