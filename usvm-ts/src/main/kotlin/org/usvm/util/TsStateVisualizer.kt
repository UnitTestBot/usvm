package org.usvm.util

import org.jacodb.ets.utils.InterproceduralCfg
import org.jacodb.ets.utils.toHighlightedDotWithCalls
import org.usvm.dataflow.ts.util.toMap
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class TsStateVisualizer : TsInterpreterObserver, UMachineObserver<TsState> {
    override fun onStatePeeked(state: TsState) {
        state.renderGraph()
    }
}

fun TsState.renderGraph() {
    val graph = InterproceduralCfg(main = entrypoint.cfg, callees = discoveredCallees.toMap())
    val dot = graph.toHighlightedDotWithCalls(
        pathStmts = pathNode.allStatements.toSet(),
        currentStmt = currentStatement,
    )

    myRenderDot(dot)
}

@Suppress("Deprecated")
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
    val svgFile = outDir.resolveSibling("$baseName.$format")
    dotFile.writeText(dot)
    Runtime.getRuntime().exec("$dotCmd $dotFile -T$format -o $svgFile").waitFor()
    if (viewerCmd != null) {
        Runtime.getRuntime().exec("$viewerCmd $svgFile").waitFor()
    }
}
