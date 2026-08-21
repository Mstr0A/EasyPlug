package com.a0.common.sessions

import com.a0.common.engine.PlayerSnapshot
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Periodically appends player snapshots to one CSV file.
 *
 * Kept deliberately simple for a demo: no locking, no rotation, one file that just
 * keeps growing. It doesn't know about AnticheatSession directly — it's handed a
 * supplier function that returns the current players to record, so it stays
 * decoupled from the session.
 */
class HistoryRecorder(
    private val historyFile: File,
    private val interval: Duration = 3.minutes,
    private val playersSupplier: () -> List<PlayerSnapshot>,
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        historyFile.parentFile?.mkdirs()
        if (!historyFile.exists() || historyFile.length() == 0L) {
            historyFile.writeText(CSV_HEADER + "\n")
        }
    }

    /** Starts periodic recording every [interval]. */
    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (isActive) {
                    delay(interval)
                    recordSnapshot()
                }
            }
    }

    /** Stops periodic recording. The CSV file is left as-is. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Appends one snapshot to the CSV right now, independent of the timer. */
    fun recordSnapshot() {
        val takenAt = Instant.now()
        val rows = playersSupplier().joinToString(separator = "") { it.toCsvRow(takenAt) + "\n" }
        if (rows.isEmpty()) return
        historyFile.appendText(rows)
    }

    private fun PlayerSnapshot.toCsvRow(takenAt: Instant): String =
        listOf(
            takenAt.toString(),
            playerId,
            cheaterProfile ?: "",
            flagged,
            flags.joinToString("|"),
            lastSpeed,
            accuracy.speedHackPrecision,
            accuracy.speedHackRecall,
        ).joinToString(",")

    companion object {
        const val CSV_HEADER = "timestamp,player_id,cheater_profile,flagged,flags,last_speed,speed_hack_precision,speed_hack_recall"
    }
}
