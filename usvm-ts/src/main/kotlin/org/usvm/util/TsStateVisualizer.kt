package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.utils.InterproceduralCfg
import org.jacodb.ets.utils.toHighlightedDotWithCalls
import org.usvm.dataflow.ts.util.toMap
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}

class TsStateVisualizer : TsInterpreterObserver, UMachineObserver<TsState> {
    override fun onStatePeeked(state: TsState) {
        state.renderGraph()
    }
}

fun TsState.renderGraph(view: Boolean = true) {
    val graph = InterproceduralCfg(main = entrypoint.cfg, callees = discoveredCallees.toMap())
    val dot = graph.toHighlightedDotWithCalls(
        pathStmts = pathNode.allStatements.toSet(),
        currentStmt = currentStatement,
    )

    if (view ) {
        myRenderDot(dot)
    } else {
        myRenderDot(dot, viewerCmd = null)
    }
}

@Suppress("DEPRECATION")
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
    val dotFile = outDir.resolve("$baseName.dot")
    val outFile = outDir.resolveSibling("$baseName.$format")
    dotFile.writeText(dot)
    Runtime.getRuntime().exec("$dotCmd $dotFile -T$format -o $outFile").waitFor()
    logger.debug { "Rendered DOT to ${format.uppercase()}: $outFile" }
    if (viewerCmd != null) {
        Runtime.getRuntime().exec("$viewerCmd $outFile").waitFor()
    }
}
