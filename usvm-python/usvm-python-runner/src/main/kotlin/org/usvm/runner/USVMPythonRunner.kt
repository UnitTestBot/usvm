package org.usvm.runner

import mu.KLogging
import java.io.File
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

open class USVMPythonRunner(private val config: USVMPythonConfig): AutoCloseable {
    protected val serverSocketChannel: ServerSocketChannel = ServerSocketChannel.open()
    private val port: InetSocketAddress

    init {
        serverSocketChannel.socket().bind(InetSocketAddress(0))
        port = serverSocketChannel.localAddress as? InetSocketAddress
            ?: error("Couldn't cast SocketAddress ${serverSocketChannel.localAddress} to InetSocketAddress")
        logger.info("Port for usvm-python: ${port.port}")
    }

    override fun close() {
        serverSocketChannel.close()
    }

    protected fun setupEnvironment(runConfig: USVMPythonRunConfig): ProcessBuilder {
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
            port.port.toString(),
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

    companion object {
        val logger = object : KLogging() {}.logger
    }
}