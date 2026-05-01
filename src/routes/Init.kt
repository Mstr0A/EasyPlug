package com.a0.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.init() {
    route("/init") {
        get {
            call.respond(mapOf("Route" to "Root of init"))
        }
    }
}
