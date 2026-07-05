package org.usvm.ts.pbt.report

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.ts.pbt.hybrid.AnalysisMode
import org.usvm.ts.pbt.hybrid.HybridAnalyzer
import org.usvm.ts.pbt.hybrid.HybridConfig
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private const val USAGE = """
Usage: hybrid-analyzer <input.ts | dir> [options]

Options:
  --mode <PBT_ONLY|SYMBOLIC_ONLY|HYBRID|HYBRID_WITH_HINTS>   (default: HYBRID_WITH_HINTS)
  --method <name>            analyze only methods with this name
  --class <name>             analyze only methods of this class
  --seed <long>              PBT random seed (default: 0)
  --pbt-iterations <int>     PBT iteration budget (default: 2000)
  --target-timeout <sec>     per-target symbolic timeout (default: 20)
  --no-fallback              disable the hint-free fallback run
  --out <file.json>          report output path (default: hybrid-report.json)

Requires ARKANALYZER_DIR to point at a CI-pinned ArkAnalyzer build.
"""

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(USAGE)
        exitProcess(1)
    }

    val input = Path(args[0])
    var mode = AnalysisMode.HYBRID_WITH_HINTS
    var methodFilter: String? = null
    var classFilter: String? = null
    var seed = 0L
    var pbtIterations = 2_000
    var targetTimeoutSec = 20
    var hintFallback = true
    var out = Path("hybrid-report.json")

    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--mode" -> mode = AnalysisMode.valueOf(args[++i])
            "--method" -> methodFilter = args[++i]
            "--class" -> classFilter = args[++i]
            "--seed" -> seed = args[++i].toLong()
            "--pbt-iterations" -> pbtIterations = args[++i].toInt()
            "--target-timeout" -> targetTimeoutSec = args[++i].toInt()
            "--no-fallback" -> hintFallback = false
            "--out" -> out = Path(args[++i])
            else -> {
                println("Unknown option: ${args[i]}\n$USAGE")
                exitProcess(1)
            }
        }
        i++
    }

    val files: List<Path> = if (input.isDirectory()) {
        input.listDirectoryEntries().filter { it.extension == "ts" }
    } else {
        listOf(input)
    }

    println("Loading ${files.size} file(s) via ArkAnalyzer...")
    val scene = EtsScene(files.map { loadEtsFileAutoConvert(it) })

    val methods = scene.projectClasses
        .asSequence()
        .filter { classFilter == null || it.name == classFilter }
        .flatMap { it.methods }
        .filter { it.cfg.stmts.isNotEmpty() }
        .filter { !it.name.startsWith("%") && it.name != "constructor" }
        .filter { methodFilter == null || it.name == methodFilter }
        .toList()

    println("Analyzing ${methods.size} method(s) in mode $mode...")

    val config = HybridConfig(
        mode = mode,
        seed = seed,
        pbtMaxIterations = pbtIterations,
        perTargetTimeout = targetTimeoutSec.seconds,
        hintFallback = hintFallback,
    )
    val report = HybridAnalyzer(scene, config).analyze(methods)

    out.writeText(HybridReport.encode(report))
    println("Report written to $out")

    for (m in report.methods) {
        println(
            "  ${m.method}: stmt=%.1f%%, branch=%.1f%% (pbt: %s, symbolic: %s), %d ms".format(
                m.stmtCoverage * 100,
                m.branchCoverage * 100,
                m.pbt?.let { "${it.executions} runs, ${it.failures.size} failures" } ?: "-",
                m.symbolic?.let { "${it.reached}/${it.targets.size} targets" } ?: "-",
                m.totalWallMs,
            )
        )
    }
}
