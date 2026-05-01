package com.a0.plugins

import com.a0.routes.init
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // to ignore any trailing slashed that way "/test/" and "/test" are the same
        install(IgnoreTrailingSlash)

        init()
    }
}
