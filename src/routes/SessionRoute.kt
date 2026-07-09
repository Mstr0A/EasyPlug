package com.a0.routes

import com.a0.common.players.RegisterRequest
import com.a0.common.sessions.SessionManager
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

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
                    val sessionID = UUID.fromString(call.parameters["sessionID"])
                    val registerRequest = call.receive<RegisterRequest>()

                    SessionManager.registerSession(sessionID, registerRequest.players)
                }
            }
        }
    }
}
