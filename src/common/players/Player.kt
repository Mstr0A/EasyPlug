package com.a0.common.players

import kotlinx.serialization.Serializable

// Used to receive the player data in normal sessions (unused in demo)
@Serializable
data class RegisterRequest(
    val players: List<Player>,
)

// Basic example of the player class, can be adjusted as the game developers see fit
@Serializable
data class Player(
    val playerID: String,
)
