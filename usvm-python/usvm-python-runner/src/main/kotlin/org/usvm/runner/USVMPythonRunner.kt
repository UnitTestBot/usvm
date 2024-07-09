package org.usvm.runner

import mu.KLogging
import java.io.File
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

open class USVMPythonRunner(private val config: USVMPythonConfig) : AutoCloseable {
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
        val venvArgs = if (config.venvConfig == null) {
            listOf("<no_venv>")
        } else {
            listOf(
                config.venvConfig.basePath.canonicalPath,
                config.venvConfig.libPath.canonicalPath,
                config.venvConfig.binPath.canonicalPath
            )
        }
        val functionConfigArgs = when (val funcConfig = runConfig.callableConfig) {
            is USVMPythonFunctionConfig -> listOf(funcConfig.module, funcConfig.name, "<no_class>")
            is USVMPythonMethodConfig -> listOf(funcConfig.module, funcConfig.name, funcConfig.cls)
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
            *functionConfigArgs.toTypedArray(),
            runConfig.timeoutPerRunMs.toString(),
            runConfig.timeoutMs.toString(),
            config.pathSelector.name,
            *venvArgs.toTypedArray(),
            *config.roots.toList().toTypedArray()
        )

        val processBuilder = ProcessBuilder(args)
        val env = processBuilder.environment()
        if (System.getProperty("os.name")!!.lowercase().startsWith("windows")) {
            env["PATH"] = (System.getProperty("PATH")?.let { "$it:" }.orEmpty()) +
                "${File(layout.cpythonPath, "DLLs").canonicalPath};${layout.cpythonPath.canonicalPath}"
        } else {
            env["LD_LIBRARY_PATH"] = "${File(layout.cpythonPath, "lib").canonicalPath}:" +
                layout.cpythonPath.canonicalPath
            env["LD_PRELOAD"] = File(layout.cpythonPath, "lib/libpython3.so").canonicalPath
        }
        env["PYTHONHOME"] = layout.cpythonPath.canonicalPath

        return processBuilder
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
