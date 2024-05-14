package org.usvm.plugins

import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.usvm.gameserver.*
import org.usvm.errors.AlreadyInGameError
import org.usvm.errors.HandledError
import org.usvm.errors.NotStartedYetError
import java.time.*
import java.util.*
import kotlin.collections.LinkedHashSet

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
        webSocket("/gameServer") {
            val thisConnection = Connection(this)
            connections += thisConnection
            try {
                while (true) {
                    val rawInputMessage = receiveDeserialized<RawInputMessage>()
                    when (val inputBody = rawInputMessage.toInputBody()) {
                        is ServerStop -> break
                        is Step -> throw NotStartedYetError()
                        is Start -> thisConnection.isInGame {
                            when (it) {
                                true -> throw AlreadyInGameError()
                                false -> {
                                    thisConnection.startGame {
                                        val explorationResult = randomExplorer(
                                            inputBody,
                                            { thisConnection.getStep() },
                                            { moveReward -> sendSerialized(moveReward.toRawOutputMessage()) })
                                        thisConnection.finishGame { sendSerialized(explorationResult.toRawOutputMessage()) }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: WebsocketDeserializeException) {
                this@configureSockets.log.error("An error occurred during deserialization: ${e.localizedMessage}")
            } catch (e: HandledError) {
                this@configureSockets.log.error(e.localizedMessage)
            } finally {
                this@configureSockets.log.info("Closing connection")
            }
        }
    }
}