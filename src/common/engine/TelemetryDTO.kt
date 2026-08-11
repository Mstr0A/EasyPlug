package com.a0.common.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryDTO(
    @SerialName("player_id")
    val playerId: String,
    // Time.get_unix_time_from_system() in Godot returns seconds (float), not ms.
    val timestamp: Double,
    // Movement
    @SerialName("pos_x")
    val posX: Float,
    @SerialName("pos_y")
    val posY: Float,
    @SerialName("pos_z")
    val posZ: Float,
    // Status
    val health: Float,
    // Ground truth (bots set a real profile/flags; human client sends "HUMAN_PLAYER" + its own demo toggle)
    @SerialName("cheater_profile")
    val cheaterProfile: String? = null,
    @SerialName("ground_truth_speed_hack")
    val groundTruthSpeedHack: Boolean = false,
    @SerialName("ground_truth_aimbot")
    val groundTruthAimbot: Boolean = false,
)
