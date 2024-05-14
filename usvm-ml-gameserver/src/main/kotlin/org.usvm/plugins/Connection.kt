package org.usvm.plugins

import io.ktor.server.websocket.*
import org.usvm.errors.AlreadyInGameError
import org.usvm.errors.InterruptedError
import org.usvm.gameserver.*
import java.util.concurrent.atomic.AtomicBoolean

class Connection(val session: WebSocketServerSession) {
    private var isMapRunning = AtomicBoolean(false)

    suspend fun startGame(continuation: suspend (() -> Any)): Any {
        this.isMapRunning.set(true)
        return continuation()
    }

    suspend fun finishGame(continuation: suspend () -> Any): Any {
        this.isMapRunning.set(false)
        return continuation()
    }

    suspend fun isInGame(continuation: suspend (isInGame: Boolean) -> Any): Any {
        return continuation(this.isMapRunning.get())
    }

    suspend fun getStep(): Step {
        val rawInputMessage = session.receiveDeserialized<RawInputMessage>()
        when (val inputBody = rawInputMessage.toInputBody()) {
            is Start -> throw AlreadyInGameError()
            is Step -> return inputBody
            is ServerStop -> throw InterruptedError()
        }
    }
}