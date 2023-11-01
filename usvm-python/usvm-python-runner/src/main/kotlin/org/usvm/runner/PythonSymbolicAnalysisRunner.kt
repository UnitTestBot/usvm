package org.usvm.runner

import mu.KLogging
import java.io.BufferedReader
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit

interface PythonSymbolicAnalysisRunner: AutoCloseable {
    fun analyze(runConfig: USVMPythonRunConfig, receiver: USVMPythonAnalysisResultReceiver, isCancelled: () -> Boolean)
}

class PythonSymbolicAnalysisRunnerImpl(
    config: USVMPythonConfig
): USVMPythonRunner(config), PythonSymbolicAnalysisRunner {

    override fun analyze(
        runConfig: USVMPythonRunConfig,
        receiver: USVMPythonAnalysisResultReceiver,
        isCancelled: () -> Boolean
    ) {
        val processBuilder = setupEnvironment(runConfig)
        val client = ClientResources(serverSocketChannel, processBuilder)
        client.use {
            println("Here!")
            // println(BufferedReader(InputStreamReader(client.process.errorStream)).readLines())
            val channel = it.clientSocketChannel
            if (channel == null) {
                logger.warn("Could not connect to usvm-python process")
                return@use
            }
            val readingThread = ReadingThread(channel, receiver, isCancelled)
            val waitingThread = WaitingThread(runConfig, channel, isCancelled)
            readingThread.start()
            waitingThread.start()
            readingThread.join()
            waitingThread.join()
        }
        if (!client.process.isAlive && client.process.exitValue() != 0) {
            logger.warn("usvm-python process ended with non-null value")
        }
    }

    class ReadingThread(
        private val channel: SocketChannel,
        private val receiver: USVMPythonAnalysisResultReceiver,
        private val isCancelled: () -> Boolean
    ): Thread() {
        override fun run() {
            try {
                val input = BufferedReader(Channels.newReader(channel, "UTF-8"))
                while (!isCancelled()) {
                    val byteStr = input.readLine() ?: break
                    receiver.receivePickledInputValues(byteStr)
                }
                channel.close()
            } catch (_: ClosedChannelException) {
                logger.info("Interrupted usvm-python channel")
            }
        }
    }

    class WaitingThread(
        private val runConfig: USVMPythonRunConfig,
        private val channel: SocketChannel,
        private val isCancelled: () -> Boolean
    ): Thread() {
        override fun run() {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < runConfig.timeoutMs && channel.isOpen) {
                if (isCancelled()) {
                    channel.close()
                }
                TimeUnit.MILLISECONDS.sleep(200)
            }
            channel.close()
        }
    }

    override fun close() {
        serverSocketChannel.close()
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}

class ClientResources(
    serverSocketChannel: ServerSocketChannel,
    processBuilder: ProcessBuilder
): AutoCloseable {
    val process: Process
    val clientSocketChannel: SocketChannel?

    init {
        process = processBuilder.start()
        clientSocketChannel = serverSocketChannel.accept()
    }

    override fun close() {
        clientSocketChannel?.close()
        process.destroy()
    }
}