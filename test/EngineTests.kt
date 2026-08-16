
import com.a0.common.engine.AnticheatSession
import com.a0.common.engine.TelemetryDTO
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnticheatSessionTest {
    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun session() = AnticheatSession()

    private fun frame(
        playerId: String = "BOT_1",
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f,
        health: Float = 100f,
        timestamp: Double = 0.0,
        isRespawn: Boolean = false,
        groundTruthSpeedHack: Boolean = false,
        cheaterProfile: String? = "LEGIT",
    ) = TelemetryDTO(
        playerId = playerId,
        posX = posX,
        posY = posY,
        posZ = posZ,
        health = health,
        timestamp = timestamp,
        isRespawn = isRespawn,
        groundTruthSpeedHack = groundTruthSpeedHack,
        cheaterProfile = cheaterProfile,
    )

    // ── First frame ──────────────────────────────────────────────────────────

    @Test
    fun `first frame sets baseline and does not flag`() {
        val ac = session()
        ac.process(frame(posX = 5f, posY = 0f, posZ = 5f, timestamp = 0.0))

        val snap = ac.snapshot().first()
        assertFalse(snap.flagged, "First frame should never be flagged")
        assertFalse("SPEED_HACK" in snap.flags)
    }

    // ── Normal movement ──────────────────────────────────────────────────────

    @Test
    fun `normal movement under threshold is not flagged`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, timestamp = 0.0))
        // 5 units in 1 second = 5 u/s, under MAX_LEGIT_SPEED (7)
        ac.process(frame(posX = 5f, posZ = 0f, timestamp = 1.0))

        val snap = ac.snapshot().first()
        assertFalse(snap.flagged)
        assertFalse("SPEED_HACK" in snap.flags)
    }

    // ── Speed hack ───────────────────────────────────────────────────────────

    @Test
    fun `speed hack over threshold is flagged`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, timestamp = 0.0, groundTruthSpeedHack = true))
        // 17.5 units in 1 second = RAGE bot speed (BASE_SPEED * 3.5), well over threshold
        ac.process(frame(posX = 17.5f, posZ = 0f, timestamp = 1.0, groundTruthSpeedHack = true))

        val snap = ac.snapshot().first()
        assertTrue(snap.flagged)
        assertTrue("SPEED_HACK" in snap.flags)
    }

    @Test
    fun `speed exactly at threshold is not flagged`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, timestamp = 0.0))
        // Exactly MAX_LEGIT_SPEED = 7 u/s
        ac.process(frame(posX = 7f, posZ = 0f, timestamp = 1.0))

        val snap = ac.snapshot().first()
        assertFalse(snap.flagged)
    }

    @Test
    fun `speed just over threshold is flagged`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, timestamp = 0.0))
        // 7.1 u/s — just over MAX_LEGIT_SPEED
        ac.process(frame(posX = 7.1f, posZ = 0f, timestamp = 1.0))

        val snap = ac.snapshot().first()
        assertTrue(snap.flagged)
    }

    // ── Respawn guard ────────────────────────────────────────────────────────

    @Test
    fun `explicit respawn flag skips speed check`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, timestamp = 0.0))
        // Massive position jump but isRespawn = true
        ac.process(frame(posX = 100f, posZ = 100f, timestamp = 1.0, isRespawn = true))

        val snap = ac.snapshot().first()
        assertFalse(snap.flagged, "Explicit respawn flag should suppress speed check")
    }

    @Test
    fun `health jump over threshold triggers respawn guard`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, health = 10f, timestamp = 0.0))
        // Health jumps from 10 to 100 = +90, over RESPAWN_HEALTH_JUMP (25)
        ac.process(frame(posX = 100f, posZ = 100f, health = 100f, timestamp = 1.0))

        val snap = ac.snapshot().first()
        assertFalse(snap.flagged, "Health jump should be detected as respawn")
    }

    @Test
    fun `normal health drop does not trigger respawn guard`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, health = 100f, timestamp = 0.0))
        // Health drops from 100 to 85, normal damage — speed check should still run
        ac.process(frame(posX = 17.5f, posZ = 0f, health = 85f, timestamp = 1.0))

        val snap = ac.snapshot().first()
        assertTrue(snap.flagged, "Normal damage should not suppress speed detection")
    }

    // ── Timestamp guard ──────────────────────────────────────────────────────

    @Test
    fun `frame with dt below MIN_DT is skipped`() {
        val ac = session()
        ac.process(frame(posX = 0f, posZ = 0f, timestamp = 0.0))
        // dt = 0.0005s, below MIN_DT (0.001) — frame skipped, no flag
        ac.process(frame(posX = 100f, posZ = 0f, timestamp = 0.0005))

        val snap = ac.snapshot().first()
        assertFalse(snap.flagged, "Sub-MIN_DT frame should be skipped entirely")
    }

    // ── Multi-player isolation ───────────────────────────────────────────────

    @Test
    fun `players are tracked independently`() {
        val ac = session()

        ac.process(frame(playerId = "BOT_LEGIT", posX = 0f, posZ = 0f, timestamp = 0.0))
        ac.process(frame(playerId = "BOT_LEGIT", posX = 5f, posZ = 0f, timestamp = 1.0))

        ac.process(frame(playerId = "BOT_RAGE", posX = 0f, posZ = 0f, timestamp = 0.0))
        ac.process(frame(playerId = "BOT_RAGE", posX = 17.5f, posZ = 0f, timestamp = 1.0))

        val snapshots = ac.snapshot().associateBy { it.playerId }
        assertFalse(snapshots["BOT_LEGIT"]!!.flagged)
        assertTrue(snapshots["BOT_RAGE"]!!.flagged)
    }

    // ── Telemetry DTO parsing ────────────────────────────────────────────────

    @Test
    fun `valid telemetry JSON deserializes correctly`() {
        val json =
            """
            {
                "playerId": "BOT_1",
                "timestamp": 1.0,
                "posX": 3.5,
                "posY": 0.0,
                "posZ": 2.1,
                "health": 100.0,
                "isRespawn": false,
                "cheaterProfile": "LEGIT",
                "groundTruthSpeedHack": false
            }
            """.trimIndent()

        val dto = Json.decodeFromString<TelemetryDTO>(json)
        assertEquals("BOT_1", dto.playerId)
        assertEquals(3.5f, dto.posX)
        assertEquals(100f, dto.health)
        assertFalse(dto.groundTruthSpeedHack)
    }

    @Test
    fun `optional fields default correctly when missing`() {
        val json =
            """
            {
                "playerId": "BOT_2",
                "timestamp": 0.0,
                "posX": 0.0,
                "posY": 0.0,
                "posZ": 0.0
            }
            """.trimIndent()

        val dto = Json.decodeFromString<TelemetryDTO>(json)
        assertFalse(dto.isRespawn)
        assertFalse(dto.groundTruthSpeedHack)
        assertEquals(null, dto.cheaterProfile)
        assertEquals(100f, dto.health)
    }
}
