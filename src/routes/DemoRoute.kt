package com.a0.routes

import com.a0.common.engine.AnticheatSession
import com.a0.common.engine.TelemetryDTO
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

private val demoAnticheat = AnticheatSession()

fun Route.demoRoute() {
    route("/session") {
        webSocket("/demo/start") {
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        try {
                            val telemetryRaw = frame.readText()
                            val dto: TelemetryDTO = Json.decodeFromString(telemetryRaw)
                            demoAnticheat.process(dto)
                        } catch (e: Exception) {
                            // Log or drop malformed payloads safely without crashing the WebSocket
                        }
                    }
                }
            } finally {
                // no-op: demo session never ends
            }
        }

        get("/demo/status") {
            call.respond(demoAnticheat.snapshot())
        }

        get("/demo/metrics") {
            call.respond(demoAnticheat.globalPerformanceReport())
        }
    }

    get("/dashboard/demo") {
        call.respondText(DASHBOARD_HTML, ContentType.Text.Html)
    }
}

private val DASHBOARD_HTML =
    object {}.javaClass.getResource("/dashboard.html")?.readText()
        ?: "<html><body><h3>Dashboard HTML resource not found.</h3></body></html>"
