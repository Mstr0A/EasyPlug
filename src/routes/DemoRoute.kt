package com.a0.routes

import com.a0.common.engine.AnticheatSession
import com.a0.common.engine.TelemetryDTO
import com.a0.common.sessions.HistoryReader
import com.a0.common.sessions.HistoryRecorder
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.io.File

private val demoAnticheat = AnticheatSession()

private val demoHistoryFile = File("history/demo-session.csv")

private val demoHistoryRecorder =
    HistoryRecorder(
        historyFile = demoHistoryFile,
        playersSupplier = { demoAnticheat.snapshot() },
    ).also { it.start() }

private val demoHistoryReader = HistoryReader(demoHistoryFile)

fun Route.demoRoute() {
    route("/") {
        get {
            call.respondText(INDEX_HTML, ContentType.Text.Html)
        }
    }

    route("/session/demo") {
        webSocket("/start") {
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

        // RAW
        get("/status") {
            call.respond(demoAnticheat.snapshot())
        }

        // RAW
        get("/metrics/raw") {
            call.respond(demoAnticheat.globalPerformanceReport())
        }

        get("/metrics") {
            call.respondText(METRICS_HTML, ContentType.Text.Html)
        }

        // RAW
        get("/history/raw") {
            call.respond(demoHistoryReader.readAllPlayers())
        }

        get("/history") {
            call.respondText(HISTORY_HTML, ContentType.Text.Html)
        }

        get("/dashboard") {
            call.respondText(DASHBOARD_HTML, ContentType.Text.Html)
        }
    }
}

private val INDEX_HTML =
    object {}.javaClass.getResource("/html/index.html")?.readText()
        ?: "<html><body><h3>Index HTML resource not found.</h3></body></html>"

private val DASHBOARD_HTML =
    object {}.javaClass.getResource("/html/dashboard.html")?.readText()
        ?: "<html><body><h3>Dashboard HTML resource not found.</h3></body></html>"

private val METRICS_HTML =
    object {}.javaClass.getResource("/html/metrics.html")?.readText()
        ?: "<html><body><h3>Metrics HTML resource not found.</h3></body></html>"

private val HISTORY_HTML =
    object {}.javaClass.getResource("/html/history.html")?.readText()
        ?: "<html><body><h3>History HTML resource not found.</h3></body></html>"
