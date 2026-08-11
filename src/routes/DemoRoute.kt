package com.a0.routes

import com.a0.common.engine.AnticheatSession
import com.a0.common.engine.TelemetryDTO
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

// Single-session demo: no registration, no session UUIDs. One anticheat
// instance for the one game running. Real session management lives in
// the old implementation
private val demoAnticheat = AnticheatSession()

fun Route.demoRoute() {
    webSocket("/session/demo/start") {
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val telemetryRaw = frame.readText()
                    val dto: TelemetryDTO = Json.decodeFromString(telemetryRaw)

                    demoAnticheat.process(dto)
                }
            }
        } finally {
            // no-op: demo session never "ends", it just keeps accepting telemetry
        }
    }

    get("/session/demo/status") {
        call.respond(demoAnticheat.snapshot())
    }

    get("/dashboard/demo") {
        call.respondText(DASHBOARD_HTML, ContentType.Text.Html)
    }
}

private val DASHBOARD_HTML = object {}.javaClass.getResource("/dashboard.html")!!.readText()
