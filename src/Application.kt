package com.a0

import com.a0.plugins.configureMonitoring
import com.a0.plugins.configureRouting
import com.a0.plugins.configureSerialization
import com.a0.plugins.configureSockets
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureSockets()
    configureRouting()
}
