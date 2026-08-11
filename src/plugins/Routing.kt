package com.a0.plugins

import com.a0.routes.demoRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        install(IgnoreTrailingSlash)

        demoRoute()
    }
}
