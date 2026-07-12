package com.a0.common.utility

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.util.*

fun String.toUUID(): UUID = UUID.fromString(this)

suspend fun ApplicationCall.receiveSessionID(): UUID? =
    try {
        parameters["sessionID"]!!.toUUID()
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to "invalid session ID"))
        null
    }
