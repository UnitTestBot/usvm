package org.usvm.runner

import org.usvm.machine.saving.PickledObjectSender
import java.io.PrintWriter
import java.net.Socket

class PickledObjectCommunicator(
    ip: String,
    port: Int
): AutoCloseable, PickledObjectSender() {
    private val clientSocket = Socket(ip, port)
    private val writer = PrintWriter(clientSocket.getOutputStream())
    override suspend fun sendPickledInputs(pickledInput: String) {
        writer.println(pickledInput)
        writer.flush()
    }

    override fun close() {
        writer.close()
        clientSocket.close()
    }
}
