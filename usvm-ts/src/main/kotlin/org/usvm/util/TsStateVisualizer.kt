package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.utils.InterproceduralCfg
import org.jacodb.ets.utils.toHighlightedDotWithCalls
import org.usvm.dataflow.ts.util.toMap
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.StringTokenizer
import java.util.concurrent.CancellationException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

class TsStateVisualizer : TsInterpreterObserver, UMachineObserver<TsState> {
    override fun onStatePeeked(state: TsState) {
        try {
            state.renderGraph()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: InterruptedIOException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not visualize state at ${state.currentStatement}" }
        }
    }
}

fun TsState.renderGraph(view: Boolean = true) {
    val graph = InterproceduralCfg(main = entrypoint.cfg, callees = discoveredCallees.toMap())
    val dot = graph.toHighlightedDotWithCalls(
        pathStmts = pathNode.allStatements.toSet(),
        currentStmt = currentStatement,
    )

    if (view) {
        myRenderDot(dot)
    } else {
        myRenderDot(dot, viewerCmd = null)
    }
}

fun myRenderDot(
    dot: String,
    outDir: Path = createTempDirectory(),
    baseName: String = "cfg",
    dotCmd: String = "dot",
    format: String = "svg", // "svg", "png", "pdf"
    viewerCmd: String? = when {
        System.getProperty("os.name").startsWith("Mac") -> "open"
        System.getProperty("os.name").startsWith("Win") -> "cmd /c start"
        else -> "xdg-open"
    },
) {
    val dotCommand = splitCommandPrefix(dotCmd)
    val viewerCommand = viewerCmd?.let(::splitCommandPrefix)

    myRenderDot(
        dot = dot,
        outDir = outDir,
        baseName = baseName,
        dotCommand = dotCommand,
        format = format,
        viewerCommand = viewerCommand,
    )
}

fun myRenderDot(
    dot: String,
    outDir: Path,
    baseName: String,
    dotCommand: List<String>,
    format: String,
    viewerCommand: List<String>?,
) {
    require(dotCommand.isNotEmpty()) { "Graphviz command must not be empty" }
    require(viewerCommand == null || viewerCommand.isNotEmpty()) { "Viewer command must not be empty" }

    val dotFile = outDir.resolve("$baseName.input.dot")
    val outFile = outDir.resolve("$baseName.$format")
    dotFile.writeText(dot)
    Files.deleteIfExists(outFile)

    val renderCommand = renderCommandArguments(dotCommand, dotFile, outFile, format)
    val renderExitCode = runCommand(renderCommand)
    checkRenderResult(renderExitCode, outFile)

    logger.debug { "Rendered DOT to ${format.uppercase()}: $outFile" }
    if (viewerCommand != null) {
        val openCommand = viewerCommand + outFile.toString()
        val viewerExitCode = runCommand(openCommand)
        check(viewerExitCode == 0) { "Viewer exited with code $viewerExitCode for $outFile" }
    }
}

internal fun splitCommandPrefix(command: String): List<String> {
    val tokenizer = StringTokenizer(command)
    val tokens = buildList {
        while (tokenizer.hasMoreTokens()) {
            add(tokenizer.nextToken())
        }
    }
    require(tokens.isNotEmpty()) { "Command must not be blank" }

    return if (tokens == listOf("cmd", "/c", "start")) tokens + "" else tokens
}

internal fun renderCommandArguments(
    dotCommand: List<String>,
    dotFile: Path,
    outFile: Path,
    format: String,
): List<String> = dotCommand + listOf(dotFile.toString(), "-T$format", "-o", outFile.toString())

internal fun checkRenderResult(exitCode: Int, outFile: Path) {
    check(exitCode == 0) { "Graphviz exited with code $exitCode for $outFile" }
    check(Files.isRegularFile(outFile)) { "Graphviz did not create $outFile" }
}

private fun runCommand(command: List<String>): Int {
    val process = ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()

    return try {
        process.waitFor()
    } catch (e: InterruptedException) {
        process.destroyForcibly()
        throw e
    }
}
