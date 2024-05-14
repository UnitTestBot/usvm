package org.usvm

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import org.usvm.errors.UnknownModeError
import org.usvm.plugins.configureSockets


fun main(args: Array<String>) {
    val parser = ArgParser("usvm.ML.GameServer.Runner")
    val mode by parser.option(
        ArgType.String,
        fullName = "mode",
        shortName = "mode",
        description = "Mode to run application. Server --- to train network, Generator --- to generate data for training (not impl yet)."
    )
    val port by parser.option(
        ArgType.Int,
        fullName = "port",
        shortName = "port",
        description = "Port to communicate with game client"
    ).default(8100)
    parser.parse(args)

    when (mode?.lowercase()) {
        "server" -> {
            embeddedServer(Netty, port = port) { module() }.start(wait = true)
        }

        "generator" -> TODO()
        else -> throw UnknownModeError(mode ?: "")
    }
}

fun Application.module() {
    configureSockets()
}
