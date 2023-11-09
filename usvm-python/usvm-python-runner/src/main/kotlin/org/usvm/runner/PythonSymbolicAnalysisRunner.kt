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
        val readingThread = ReadingThread(serverSocketChannel, receiver, isCancelled)
        val waitingThread = WaitingThread(process, runConfig, readingThread, isCancelled)
        try {
            readingThread.start()
            waitingThread.start()
            readingThread.join()
            waitingThread.join()
            if (!process.isAlive && process.exitValue() != 0) {
                logger.warn("usvm-python process ended with non-null value")
            }
        } finally {
            process.destroyForcibly()
            readingThread.client?.close()
        }
    }

    class ReadingThread(
        private val serverSocketChannel: ServerSocketChannel,
        private val receiver: USVMPythonAnalysisResultReceiver,
        private val isCancelled: () -> Boolean
    ): Thread() {
        var client: SocketChannel? = null
        override fun run() {
            try {
                client = serverSocketChannel.accept()
                client?.use {
                    if (client == null) {
                        logger.warn("Could not connect to usvm-python process")
                        return@use
                    }
                    val input = BufferedReader(Channels.newReader(client!!, "UTF-8"))
                    while (!isCancelled()) {
                        val byteStr = input.readLine() ?: break
                        receiver.receivePickledInputValues(byteStr)
                    }
                }
            } catch (_: ClosedChannelException) {
                logger.info("Interrupted usvm-python channel")
            } catch (_: InterruptedException) {
                logger.info("Interrupted usvm-python thread")
            }
        }
    }

    class WaitingThread(
        private val process: Process,
        private val runConfig: USVMPythonRunConfig,
        private val readingThread: Thread,
        private val isCancelled: () -> Boolean
    ): Thread() {
        override fun run() {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < runConfig.timeoutMs && readingThread.isAlive && process.isAlive) {
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