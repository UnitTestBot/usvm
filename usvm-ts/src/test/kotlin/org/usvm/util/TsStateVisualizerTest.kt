package org.usvm.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TsStateVisualizerTest {
    @Test
    fun `DOT output cannot delete input and stays inside its render directory`(@TempDir directory: Path) {
        val renderDirectory = Files.createDirectory(directory.resolve("render with spaces"))
        val dot = "digraph test {}"
        val staleOutput = renderDirectory.resolve("cfg.dot")
        val siblingOutput = directory.resolve("cfg.dot")
        staleOutput.writeText("stale")
        siblingOutput.writeText("sibling")
        val nonexistentRenderer = directory.resolve("missing renderer").toString()

        assertFailsWith<IOException> {
            myRenderDot(
                dot = dot,
                outDir = renderDirectory,
                baseName = "cfg",
                dotCommand = listOf(nonexistentRenderer),
                format = "dot",
                viewerCommand = null,
            )
        }

        assertEquals(dot, renderDirectory.resolve("cfg.input.dot").readText())
        assertFalse(Files.exists(staleOutput))
        assertEquals("sibling", siblingOutput.readText())
    }

    @Test
    fun `render command preserves paths with spaces as single arguments`(@TempDir directory: Path) {
        val dotFile = directory.resolve("my graphs").resolve("cfg.dot")
        val outFile = directory.resolve("my graphs").resolve("cfg.svg")
        val executable = directory.resolve("Graphviz tools").resolve("dot").toString()

        val arguments = renderCommandArguments(
            dotCommand = listOf(executable, "-Gdpi=100"),
            dotFile = dotFile,
            outFile = outFile,
            format = "svg",
        )

        assertEquals(listOf(executable, "-Gdpi=100", dotFile.toString(), "-Tsvg", "-o", outFile.toString()), arguments)
    }

    @Test
    fun `legacy Windows viewer prefix retains an empty start title`() {
        val arguments = splitCommandPrefix(command = "cmd /c start")

        assertEquals(listOf("cmd", "/c", "start", ""), arguments)
    }

    @Test
    fun `renderer failure does not count as a successful image`(@TempDir directory: Path) {
        val outFile = directory.resolve("cfg.svg")

        val exitFailure = assertFailsWith<IllegalStateException> {
            checkRenderResult(exitCode = 2, outFile = outFile)
        }
        val missingOutput = assertFailsWith<IllegalStateException> {
            checkRenderResult(exitCode = 0, outFile = outFile)
        }

        assertTrue(exitFailure.message.orEmpty().contains("code 2"))
        assertTrue(missingOutput.message.orEmpty().contains("did not create"))
    }

    @Test
    fun `renderer success accepts an existing image`(@TempDir directory: Path) {
        val outFile = directory.resolve("cfg.svg")
        outFile.writeText("<svg/>")

        checkRenderResult(exitCode = 0, outFile = outFile)
    }
}
