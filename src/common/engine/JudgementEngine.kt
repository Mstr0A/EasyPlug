package com.a0.common.engine

import com.a0.common.sessions.Session

// Skeleton for how the engine would work
class JudgementEngine(
    val session: Session,
) {
    fun goLive(telemetry: TelemetryDTO) {
        // Telemetry processing goes here
    }

    fun shutdown() {
        // Shutdown and saving to DB goes here
    }
}
