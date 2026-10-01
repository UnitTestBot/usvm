package org.usvm.ts.calls

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertFails
import kotlin.test.assertFalse

class CallsSourceProjectTest {
    @Test
    fun `single and multi file conversion failures remove partial IR`() {
        checkCleanup(frontendExitCode = 1)
    }

    @Test
    fun `single and multi file IR decoding failures remove generated output`() {
        checkCleanup(frontendExitCode = 0)
    }

    private fun checkCleanup(frontendExitCode: Int) {
        val root = createTempDirectory(prefix = "calls-frontend-cleanup-")
        val frontend = root.resolve("frontend")
        val script = frontend.resolve("dist/index.js")
        val recordedPath = script.resolveSibling("generated-path")
        val sourceRoot = root.resolve("sources").createDirectories()
        val source = sourceRoot.resolve("entry.ts")
        val previousFrontend = System.getProperty("ets.frontend.dir")
        script.parent.createDirectories()
        script.writeText(
            """
            const fs = require('node:fs');
            const path = require('node:path');
            const output = process.argv.filter(arg => arg !== '-v').at(-1);
            fs.writeFileSync(path.join(__dirname, 'generated-path'), output);
            const file = fs.statSync(output).isDirectory() ? path.join(output, 'partial.json') : output;
            fs.writeFileSync(file, '{');
            process.exit($frontendExitCode);
            """.trimIndent(),
        )
        System.setProperty("ets.frontend.dir", frontend.toString())

        try {
            source.writeText("export function entry(value: number): number { return value; }")
            assertFails { loadCallsSourceProject(sourceRoot = sourceRoot, source = source) }
            assertFalse(Files.exists(Path.of(recordedPath.readText())))

            sourceRoot.resolve("helper.ts").writeText("export function helper(value: number): number { return value; }")
            source.writeText(
                "import { helper } from './helper'; " +
                    "export function entry(value: number): number { return helper(value); }",
            )
            assertFails { loadCallsSourceProject(sourceRoot = sourceRoot, source = source) }
            assertFalse(Files.exists(Path.of(recordedPath.readText())))
        } finally {
            if (previousFrontend == null) {
                System.clearProperty("ets.frontend.dir")
            } else {
                System.setProperty("ets.frontend.dir", previousFrontend)
            }
            root.toFile().deleteRecursively()
        }
    }
}
