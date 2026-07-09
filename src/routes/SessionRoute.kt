package com.a0.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoute() {
    route("/session") {
        route("/new") {
            get {
                call.respond(mapOf("New session" to "Created"))
            }
        }
    }
}
