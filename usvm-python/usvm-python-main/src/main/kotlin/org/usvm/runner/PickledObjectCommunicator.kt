package org.usvm.runner

import java.io.PrintWriter
import java.net.Socket

class PickledObjectCommunicator(
    ip: String,
    port: Int,
) : AutoCloseable {
    private val clientSocket = Socket(ip, port)
    private val writer = PrintWriter(clientSocket.getOutputStream())

    fun sendPickledInputs(pickledInput: String) {
        writer.println(pickledInput)
        writer.flush()
    }

    override fun close() {
        writer.close()
        clientSocket.close()
    }
}
