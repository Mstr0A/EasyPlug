package com.a0.routes

import com.a0.common.engine.TelemetryDTO
import com.a0.common.players.RegisterRequest
import com.a0.common.sessions.SessionManager
import com.a0.common.utility.receiveSessionID
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Route.sessionRoute() {
    authenticate {
        route("/session") {
            // Creating new sessions
            route("/new") {
                get {
                    val newSessionUUID = SessionManager.createNewSession()
                    call.respond(mapOf("created" to newSessionUUID.toString()))
                }
            }

            // All work after session creation
            route("/{sessionID}") {
                // Register the players
                route("/register") {
                    post {
                        val sessionID = call.receiveSessionID() ?: return@post

                        if (sessionID !in SessionManager.newSessions) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "unknown session"))
                            return@post
                        }

                        val registerRequest =
                            try {
                                call.receive<RegisterRequest>()
                            } catch (_: ContentTransformationException) {
                                call.respond(
                                    HttpStatusCode.UnprocessableEntity,
                                    mapOf("error" to "cannot process provided data"),
                                )
                                return@post
                            }

                        SessionManager.registerSession(sessionID, registerRequest.players)

                        call.respond(HttpStatusCode.NoContent)
                    }
                }

                // Cancellation
                route("/cancel") {
                    post {
                        val sessionID = call.receiveSessionID() ?: return@post

                        SessionManager.cancelSession(sessionID)

                        call.respond(HttpStatusCode.NoContent)
                    }
                }

                // If all is clear, start the session
                webSocket("/start") {
                    val sessionID = call.receiveSessionID() ?: return@webSocket

                    if (sessionID !in SessionManager.registeredSessions) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unknown session"))
                        return@webSocket
                    }

                    val runningSession = SessionManager.startSession(sessionID)

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val telemetryRaw = frame.readText()
                                val dto: TelemetryDTO = Json.decodeFromString(telemetryRaw)

                                runningSession.anticheat.process(dto)
                            }
                        }
                    } finally {
                        SessionManager.endSession(sessionID)
                    }
                }
            } // /{sessionID}
        } // /session
    } // Authenticate
}
