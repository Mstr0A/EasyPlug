# Easy Plug Anti-Cheat

Server-side heuristic anti-cheat engine built with Ktor. Receives live telemetry from a game server over WebSocket, runs behavioral analysis, and exposes a live dashboard for monitoring.

No client-side installation. No kernel drivers. Just a sidecar service your game server talks to.

---

## How It Works

The game server connects to the engine via WebSocket and streams telemetry frames once per second per player. The engine calculates each player's movement speed using 3D Euclidean distance over time and flags anyone exceeding the legitimate movement threshold. Results are available live via a polling dashboard.

---

## Running

Requires JDK 17+.

```bash
./amper run
```

Server starts at `http://0.0.0.0:8080`. The dashboard is available at `http://localhost:8080/dashboard/demo`.

---

## Demo Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `WS` | `/session/demo/start` | WebSocket endpoint — stream telemetry frames here |
| `GET` | `/session/demo/status` | Returns current live snapshot of all tracked players |
| `GET` | `/dashboard/demo` | Live monitoring dashboard |

---

## Telemetry Frame Format

Frames are sent as JSON over the WebSocket connection:

```json
{
  "playerId": "BOT_1",
  "timestamp": 1234567890.123,
  "posX": 3.5,
  "posY": 0.0,
  "posZ": 2.1,
  "health": 100.0,
  "isRespawn": false,
  "cheaterProfile": "LEGIT",
  "groundTruthSpeedHack": false
}
```

`cheaterProfile` and `groundTruthSpeedHack` are optional and used for accuracy tracking in simulation contexts.

---

## Detection Thresholds

| Constant | Value | Reason |
|----------|-------|--------|
| `MAX_LEGIT_SPEED` | 7.0 u/s | Base movement speed (5.0) + slack for acceleration and jump arcs |
| `SPEED_STREAK_TO_FLAG` | 1 | Frames above threshold before flagging |
| `MIN_DT` | 0.001s | Minimum time delta to trust a speed calculation |
| `RESPAWN_HEALTH_JUMP` | 25f | Health increase threshold for respawn detection |
| `STALE_SECONDS` | 5s | Player removed from live snapshot after this period of inactivity |

---

## Stack

- Ktor (Netty) — WebSocket + HTTP server
- kotlinx.serialization — telemetry deserialization
- Kotlin coroutines — session concurrency
- Amper — build tooling
