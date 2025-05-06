import org.gradle.api.file.DirectoryProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

abstract class GrpcServerService : BuildService<GrpcServerService.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        val workingDir: DirectoryProperty
        val port: org.gradle.api.provider.Property<Int>
    }

    private val processRef = AtomicReference<Process?>()

    init {
        startServer()
        waitForServer()
    }

    private fun startServer() {
        println("Starting server...")
        val dir = parameters.workingDir.get().asFile
        val logFile = File("build/logs/grpc-server.log").also { it.parentFile.mkdirs() }
        val process = ProcessBuilder("npm", "run", "server")
            .directory(dir)
            // .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            // .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            .start()

        processRef.set(process)
    }

    private fun waitForServer() {
        println("Waiting for server to start...")
        val port = parameters.port.get()
        val host = "localhost"
        repeat(50) {
            try {
                Socket(host, port).use {
                    println("Server seems to be running!")
                    return
                }
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        throw RuntimeException("gRPC server not responding at $host:$port")
    }

    override fun close() {
        println("Stopping server...")
        processRef.getAndSet(null)?.destroy()
    }
}
