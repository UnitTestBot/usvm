package org.usvm.ts.pbt.report

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.ts.pbt.hybrid.AnalysisMode
import org.usvm.ts.pbt.hybrid.HybridAnalyzer
import org.usvm.ts.pbt.hybrid.HybridConfig
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private const val USAGE = """
Usage: hybrid-analyzer <input.ts | dir> [options]

Corpus selection:
  --recursive                walk subdirectories for .ts files
  --exclude <substring>      skip files whose path contains the substring (repeatable);
                             defaults always applied: .d.ts, node_modules, .test.ts, .spec.ts
  --max-files <n>            cap the number of analyzed files
  --file-scenes              load every file into its own scene (default: one
                             project scene for the whole corpus, so that
                             cross-file classes and functions resolve)
  --class <name>             only methods of this class
  --method <name>            only methods with this name

Analysis:
  --modes <M1,M2,...>        comma-separated analysis modes to run over the same corpus
                             (PBT_ONLY | SYMBOLIC_ONLY | HYBRID | HYBRID_WITH_HINTS);
                             default: HYBRID_WITH_HINTS
  --seed <long>              PBT random seed (default: 0)
  --pbt-iterations <int>     PBT iteration budget per method (default: 2000)
  --target-timeout <sec>     per-target symbolic timeout (default: 20)
  --no-fallback              disable the hint-free fallback run

Output:
  --out <prefix>             report path prefix; per mode: <prefix>-<MODE>.json
                             (default: hybrid-report)

EtsIR provider is selected by the jacodb loader (ETS_IR_PROVIDER=ts-frontend|arkanalyzer
with ETS_FRONTEND_DIR / ARKANALYZER_DIR respectively).
"""

private class Options(args: Array<String>) {
    val input: Path = Path(args[0])
    var recursive = false
    val excludes = mutableListOf(".d.ts", "node_modules", ".test.ts", ".spec.ts")
    var maxFiles = Int.MAX_VALUE
    var projectScene = true
    var classFilter: String? = null
    var methodFilter: String? = null
    var modes: List<AnalysisMode> = listOf(AnalysisMode.HYBRID_WITH_HINTS)
    var seed = 0L
    var pbtIterations = 2_000
    var targetTimeoutSec = 20
    var hintFallback = true
    var outPrefix = "hybrid-report"

    init {
        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "--recursive" -> recursive = true
                "--exclude" -> excludes += args[++i]
                "--max-files" -> maxFiles = args[++i].toInt()
                "--file-scenes" -> projectScene = false
                "--class" -> classFilter = args[++i]
                "--method" -> methodFilter = args[++i]
                "--modes" -> modes = args[++i].split(',').map { AnalysisMode.valueOf(it.trim()) }
                "--mode" -> modes = listOf(AnalysisMode.valueOf(args[++i])) // backward compat
                "--seed" -> seed = args[++i].toLong()
                "--pbt-iterations" -> pbtIterations = args[++i].toInt()
                "--target-timeout" -> targetTimeoutSec = args[++i].toInt()
                "--no-fallback" -> hintFallback = false
                "--out" -> outPrefix = args[++i].removeSuffix(".json")
                else -> {
                    println("Unknown option: ${args[i]}\n$USAGE")
                    exitProcess(1)
                }
            }
            i++
        }
    }
}

@OptIn(ExperimentalPathApi::class)
private fun collectFiles(opts: Options): List<Path> {
    val files = when {
        !opts.input.isDirectory() -> listOf(opts.input)
        opts.recursive -> opts.input.walk().filter { it.extension == "ts" }.toList()
        else -> opts.input.toFile().listFiles().orEmpty().map { it.toPath() }.filter { it.extension == "ts" }
    }
    return files
        .filter { path -> opts.excludes.none { path.pathString.contains(it) } }
        .sorted()
        .take(opts.maxFiles)
}

/**
 * Synthetic methods that must not be analyzed as entry points. Anonymous
 * arrow-function methods (`%AM0$...`) are *kept*: `export const f = (...) => ...`
 * is the dominant style in real TS corpora.
 */
private val SYNTHETIC_METHOD_NAMES = setOf("%dflt", "%instInit", "%statInit", "constructor")

private fun selectMethods(scene: EtsScene, opts: Options): List<EtsMethod> =
    scene.projectClasses
        .asSequence()
        .filter { opts.classFilter == null || it.name == opts.classFilter }
        .flatMap { it.methods }
        .filter { it.cfg.stmts.isNotEmpty() }
        .filter { it.name !in SYNTHETIC_METHOD_NAMES }
        .filter { opts.methodFilter == null || it.name == opts.methodFilter }
        .toList()

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(USAGE)
        exitProcess(1)
    }
    val opts = Options(args)

    val files = collectFiles(opts)
    println("Corpus: ${files.size} file(s)")

    // Load files, isolating frontend failures per file.
    val loadedFiles = mutableListOf<Pair<Path, EtsFile>>()
    var loadFailures = 0
    for (file in files) {
        try {
            loadedFiles += file to loadEtsFileAutoConvert(file)
        } catch (e: Throwable) {
            loadFailures++
            logger.warn { "failed to load $file: ${e.message?.take(200)}" }
        }
    }
    println("Loaded ${loadedFiles.size} file(s), $loadFailures load failure(s)")

    // Scene construction. Default: ONE scene over the whole corpus, so that
    // cross-file classes and free functions resolve (both the symbolic engine
    // and the concrete interpreter look targets up by name across the scene).
    // --file-scenes restores the old per-file isolation.
    val scenes: List<Pair<Path, EtsScene>> = if (opts.projectScene) {
        if (loadedFiles.isEmpty()) emptyList()
        else listOf(opts.input to EtsScene(loadedFiles.map { it.second }))
    } else {
        loadedFiles.map { (path, file) -> path to EtsScene(listOf(file)) }
    }
    println("Scene mode: ${if (opts.projectScene) "project (1 scene)" else "per-file (${scenes.size} scenes)"}")

    for (mode in opts.modes) {
        val config = HybridConfig(
            mode = mode,
            seed = opts.seed,
            pbtMaxIterations = opts.pbtIterations,
            perTargetTimeout = opts.targetTimeoutSec.seconds,
            hintFallback = opts.hintFallback,
        )

        val methodReports = mutableListOf<MethodReport>()
        var analysisFailures = 0

        for ((file, scene) in scenes) {
            val methods = selectMethods(scene, opts)
            for (method in methods) {
                try {
                    methodReports += HybridAnalyzer(scene, config).analyzeMethod(method)
                } catch (e: Throwable) {
                    analysisFailures++
                    logger.warn { "analysis failed for ${method.signature} in $file: ${e.message?.take(200)}" }
                }
            }
        }

        val report = HybridReport(
            config = ConfigEcho(
                mode = mode.name,
                seed = opts.seed,
                pbtMaxIterations = opts.pbtIterations,
                pbtTimeBudgetMs = config.pbtTimeBudget.inWholeMilliseconds,
                perTargetTimeoutMs = config.perTargetTimeout.inWholeMilliseconds,
                hintFallback = opts.hintFallback,
            ),
            methods = methodReports,
        )

        val outPath = Path("${opts.outPrefix}-${mode.name}.json")
        outPath.writeText(HybridReport.encode(report))

        printSummary(mode.name, report, analysisFailures)
        println("Report written to $outPath\n")
    }
}

private fun printSummary(mode: String, report: HybridReport, analysisFailures: Int) {
    val ms = report.methods
    if (ms.isEmpty()) {
        println("[$mode] no methods analyzed ($analysisFailures analysis failures)")
        return
    }
    val totalBranches = ms.sumOf { it.totalBranches }
    val coveredBranches = ms.sumOf { it.coveredBranches }
    val totalStmts = ms.sumOf { it.totalStmts }
    val coveredStmts = ms.sumOf { it.coveredStmts }
    val failures = ms.sumOf { it.pbt?.failures?.size ?: 0 }
    val unsupported = ms.sumOf { it.pbt?.unsupported ?: 0 }
    val targets = ms.flatMap { it.symbolic?.targets.orEmpty() }
    val wallMs = ms.sumOf { it.totalWallMs }

    println("[$mode] methods=${ms.size} (analysis failures: $analysisFailures)")
    println(
        "  branch coverage: $coveredBranches/$totalBranches (%.1f%%), stmt: $coveredStmts/$totalStmts (%.1f%%)"
            .format(
                if (totalBranches > 0) 100.0 * coveredBranches / totalBranches else 100.0,
                if (totalStmts > 0) 100.0 * coveredStmts / totalStmts else 100.0,
            )
    )
    println(
        "  pbt failures: $failures, unsupported executions: $unsupported; " +
            "symbolic targets: ${targets.size}, reached: ${targets.count { it.reached }}, " +
            "replay-confirmed: ${targets.count { it.replayConfirmed }}, " +
            "fallbacks: ${targets.count { it.fallbackUsed }}; wall: ${wallMs / 1000.0}s"
    )
}
