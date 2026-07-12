package com.a0.routes

import com.a0.common.players.RegisterRequest
import com.a0.common.sessions.SessionManager
import com.a0.common.utility.receiveSessionID
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun Route.sessionRoute() {
    route("/session") {
        route("/new") {
            get {
                val newSessionUUID = SessionManager.createNewSession()
                call.respond(mapOf("created" to newSessionUUID.toString()))
            }
        }

        route("/{sessionID}") {
            route("/register") {
                post {
                    val sessionID = call.receiveSessionID() ?: return@post

                    if (sessionID !in SessionManager.newSessions) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown session"))
                        return@post
                    }

                    val registerRequest = call.receive<RegisterRequest>()

                    SessionManager.registerSession(sessionID, registerRequest.players)

                    call.respond(HttpStatusCode.NoContent)
                }
            }
            route("/cancel") {
                post {
                    val sessionID = call.receiveSessionID() ?: return@post

                    SessionManager.cancelSession(sessionID)

                    call.respond(HttpStatusCode.NoContent)
                }
            }
            webSocket("/start") {
                val sessionID = call.receiveSessionID() ?: return@webSocket

                if (sessionID !in SessionManager.registeredSessions) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unknown session"))
                    return@webSocket
                }

                // Start the session

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val telemetry = frame.readText()
                            // parse json, get player uuid, run judgment
                        }
                    }
                } finally {
                    // game ended, clean up, close session
                }
            }
        }
    }
}
