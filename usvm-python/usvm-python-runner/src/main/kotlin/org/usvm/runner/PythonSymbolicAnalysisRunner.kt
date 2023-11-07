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
        val process = processBuilder.start()
        val readingThread = ReadingThread(process, serverSocketChannel, receiver, isCancelled)
        val waitingThread = WaitingThread(runConfig, readingThread, isCancelled)
        readingThread.start()
        waitingThread.start()
        readingThread.join()
        waitingThread.join()
        if (!process.isAlive && process.exitValue() != 0) {
            logger.warn("usvm-python process ended with non-null value")
        }
    }

    class ReadingThread(
        private val process: Process,
        private val serverSocketChannel: ServerSocketChannel,
        private val receiver: USVMPythonAnalysisResultReceiver,
        private val isCancelled: () -> Boolean
    ): Thread() {
        override fun run() {
            try {
                val client = ClientResources(serverSocketChannel, process)
                client.use {
                    if (client.clientSocketChannel == null) {
                        logger.warn("Could not connect to usvm-python process")
                        return@use
                    }
                    val input = BufferedReader(Channels.newReader(client.clientSocketChannel, "UTF-8"))
                    while (!isCancelled()) {
                        val byteStr = input.readLine() ?: break
                        receiver.receivePickledInputValues(byteStr)
                    }
                }
            } catch (_: ClosedChannelException) {
                logger.info("Interrupted usvm-python channel")
            }
        }
    }

    class WaitingThread(
        private val runConfig: USVMPythonRunConfig,
        private val readingThread: Thread,
        private val isCancelled: () -> Boolean
    ): Thread() {
        override fun run() {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < runConfig.timeoutMs && readingThread.isAlive) {
                if (isCancelled()) {
                    readingThread.interrupt()
                }
                TimeUnit.MILLISECONDS.sleep(100)
            }
            readingThread.interrupt()
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
    val process: Process
): AutoCloseable {
    val clientSocketChannel: SocketChannel?

    init {
        clientSocketChannel = serverSocketChannel.accept()
    }

    override fun close() {
        clientSocketChannel?.close()
        process.destroy()
    }
}