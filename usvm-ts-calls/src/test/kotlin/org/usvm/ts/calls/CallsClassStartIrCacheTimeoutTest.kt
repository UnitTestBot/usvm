package org.usvm.ts.calls

import org.jacodb.ets.utils.EtsIrGenerationTimeoutException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks the preparation process deadline with a test frontend, independently of symbolic execution. */
class CallsClassStartIrCacheTimeoutTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `startup cache honors the configured frontend preparation timeout`() {
        val frontend = directory.resolve("frontend.cjs")
        Files.writeString(frontend, "setTimeout(() => {}, 2000);\n")
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        val classpath = System.getProperty(
            "org.usvm.ts.calls.test.runtimeClasspath",
            System.getProperty("java.class.path"),
        )
        val log = directory.resolve("child.log")
        val builder = ProcessBuilder(java.toString(), "-cp", classpath, javaClass.name, directory.toString())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
        builder.environment().apply {
            put("ETS_IR_GENERATION_TIMEOUT_SEC", "1")
            put("ETS_FRONTEND_DIR", directory.toString())
            put("ETS_FRONTEND_SCRIPT", frontend.toString())
            remove("USVM_TS_STARTUP_IR_CACHE")
        }

        val process = builder.start()
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Frontend timeout regression child did not finish")
            assertEquals(expected = 0, actual = process.exitValue(), message = Files.readString(log))
        } finally {
            if (process.isAlive) process.destroyForcibly().waitFor()
        }

        assertFalse(Files.exists(directory.resolve("output/frontend-identity.json")))
    }

    companion object {
        /** A separate process supplies an isolated environment without changing other tests. */
        @JvmStatic
        fun main(arguments: Array<String>) {
            val root = Path.of(arguments.single())
            val scope = Files.createDirectories(root.resolve("source"))
            val output = Files.createDirectories(root.resolve("output"))
            val source = scope.resolve("Target.ts")
            Files.writeString(source, "export class Target {}\n")
            val case = CallsClassStartCase(
                classId = "timeout-fixture",
                identity = "fixture@Target",
                projectRevision = "a".repeat(n = 40),
                sourceRoot = scope.toString(),
                sourceFile = "Target.ts",
                className = "Target",
                inputSha256 = mapOf("Target.ts" to callsArtifactSha256(Files.readAllBytes(source))),
            )

            try {
                preparedClassStartupIr(case = case, scope = scope, output = output)
            } catch (_: EtsIrGenerationTimeoutException) {
                return
            }

            error("The configured one-second preparation deadline was ignored")
        }
    }
}
