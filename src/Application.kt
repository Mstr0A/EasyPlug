package com.a0

import com.a0.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureSockets()
    configureDatabases()
    configureAuth()
    configureRouting()
}
