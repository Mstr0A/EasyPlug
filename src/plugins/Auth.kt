package com.a0.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.apikey.*

data class AppPrincipal(
    val key: String,
)

fun Application.configureAuth() {
    val apiKey = environment.config.property("api.key").getString()

    install(Authentication) {
        apiKey {
            validate { keyFromHeader ->
                keyFromHeader
                    .takeIf { it == apiKey }
                    ?.let { AppPrincipal(it) }
            }
        }
    }
}
