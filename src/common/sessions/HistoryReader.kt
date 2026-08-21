package com.a0.common.sessions

import kotlinx.serialization.Serializable
import java.io.File

/** One recorded row for a single player at a single point in time. */
@Serializable
data class PlayerHistoryEntry(
    val timestamp: String,
    val playerId: String,
    val cheaterProfile: String?,
    val flagged: Boolean,
    val flags: List<String>,
    val lastSpeed: Float,
    val speedHackPrecision: Float,
    val speedHackRecall: Float,
)

/** Full recorded history for one player, in chronological order. */
@Serializable
data class PlayerHistory(
    val playerId: String,
    val entries: List<PlayerHistoryEntry>,
)

/**
 * Reads history written by [HistoryRecorder]. Just reads the CSV file on
 * disk — no dependency on AnticheatSession or the recorder being alive.
 */
class HistoryReader(
    private val historyFile: File,
) {
    /** All recorded rows, oldest first, regardless of player. */
    fun readAll(): List<PlayerHistoryEntry> {
        if (!historyFile.exists()) return emptyList()

        return historyFile
            .readLines()
            .drop(1) // header
            .filter { it.isNotBlank() }
            .map { it.toEntry() }
    }

    /** History for every player seen in the file, grouped and ordered chronologically per player. */
    fun readAllPlayers(): List<PlayerHistory> =
        readAll()
            .groupBy { it.playerId }
            .map { (playerId, entries) -> PlayerHistory(playerId, entries) }

    /** History for a single player, chronological. Empty if the player was never recorded. */
    fun readPlayer(playerId: String): PlayerHistory = PlayerHistory(playerId, readAll().filter { it.playerId == playerId })

    private fun String.toEntry(): PlayerHistoryEntry {
        val f = split(",")
        return PlayerHistoryEntry(
            timestamp = f[0],
            playerId = f[1],
            cheaterProfile = f[2].ifEmpty { null },
            flagged = f[3].toBoolean(),
            flags = f[4].split("|").filter { it.isNotEmpty() },
            lastSpeed = f[5].toFloat(),
            speedHackPrecision = f[6].toFloat(),
            speedHackRecall = f[7].toFloat(),
        )
    }
}
