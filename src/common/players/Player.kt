package com.a0.common.players

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val players: List<Player>,
)

@Serializable
data class Player(
    val playerID: String,
)
