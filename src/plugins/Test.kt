package com.a0.plugins

import kotlinx.serialization.Serializable

@Serializable
data class Test(
    val message: String,
    val number: Int,
)
