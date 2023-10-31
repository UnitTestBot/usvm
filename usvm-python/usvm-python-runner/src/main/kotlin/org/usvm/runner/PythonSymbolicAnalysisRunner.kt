package org.usvm.runner

import mu.KLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.InterruptibleChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit

interface PythonSymbolicAnalysisRunner: AutoCloseable {
    fun analyze(runConfig: USVMPythonRunConfig, receiver: USVMPythonAnalysisResultReceiver, isCancelled: () -> Boolean)
}

class PythonSymbolicAnalysisRunnerImpl(
    private val vacantPort: Int,
    private val config: USVMPythonConfig
): PythonSymbolicAnalysisRunner {
    private val serverSocketChannel = ServerSocketChannel.open()

    init {
        serverSocketChannel.socket().bind(InetSocketAddress("localhost", vacantPort))
    }

    override fun analyze(
        runConfig: USVMPythonRunConfig,
        receiver: USVMPythonAnalysisResultReceiver,
        isCancelled: () -> Boolean
    ) {
        val processBuilder = setupEnvironment(runConfig)
        val client = ClientResources(serverSocketChannel, processBuilder)
        client.use {
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

    private fun setupEnvironment(runConfig: USVMPythonRunConfig): ProcessBuilder {
        val layout = config.distributionLayout
        val functionConfig = when (runConfig.callableConfig) {
            is USVMPythonFunctionConfig -> runConfig.callableConfig
        }
        val args = listOf(
            config.javaCmd,
            "-Xss50m",
            "-Xmx2g",
            "-Dapproximations.path=${layout.approximationsPath.canonicalPath}",
            "-Djava.library.path=${layout.nativeLibPath.canonicalPath}",
            "-jar",
            layout.jarPath.canonicalPath,
            config.mypyBuildDir,
            vacantPort.toString(),
            functionConfig.module,
            functionConfig.name,
            runConfig.timeoutPerRunMs.toString(),
            runConfig.timeoutMs.toString()
        ) + config.roots.toList()

        val processBuilder = ProcessBuilder(args)

        val env = processBuilder.environment()
        env["LD_LIBRARY_PATH"] = "${File(layout.cpythonPath, "lib").canonicalPath}:${layout.cpythonPath.canonicalPath}"
        env["LD_PRELOAD"] = File(layout.cpythonPath, "lib/libpython3.so").canonicalPath
        env["PYTHONHOME"] = layout.cpythonPath.canonicalPath

        return processBuilder
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