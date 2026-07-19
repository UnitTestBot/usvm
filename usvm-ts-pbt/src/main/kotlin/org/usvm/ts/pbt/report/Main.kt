package org.usvm.ts.pbt.report

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.usvm.ts.pbt.hybrid.AnalysisMode
import org.usvm.ts.pbt.hybrid.HybridAnalyzer
import org.usvm.ts.pbt.hybrid.HybridConfig
import org.usvm.ts.pbt.external.ExternalCorpusInputProvider
import org.usvm.ts.pbt.external.TargetManifest
import org.usvm.ts.pbt.external.stableMethodId
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readLines
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
  --project-frontend         convert the directory as one TypeScript Program;
                             preserves relative file paths and project typing
  --class <name>             only methods of this class
  --method <name>            only methods with this name
  --method-ids <file>        only stable method IDs listed one per line

Analysis:
  --modes <M1,M2,...>        comma-separated analysis modes to run over the same corpus
                             (PBT_ONLY | SYMBOLIC_ONLY | HYBRID | HYBRID_WITH_HINTS);
                             default: HYBRID_WITH_HINTS
  --seed <long>              PBT random seed (default: 0)
  --pbt-iterations <int>     PBT iteration budget per method (default: 2000)
  --target-timeout <sec>     per-target symbolic timeout (default: 20)
  --no-fallback              disable the hint-free fallback run
  --external-inputs <file>   import an External Test Corpus JSON/JSONL (repeatable)
  --external-only            replay external inputs without internal random PBT

Output:
  --out <prefix>             report path prefix; per mode: <prefix>-<MODE>.json
                             (default: hybrid-report)
  --export-target-manifest <path>
                             write entry points and stable EtsIR branch IDs for
                             external generators
  --manifest-only            stop after writing the target manifest

EtsIR provider is selected by the jacodb loader (ETS_IR_PROVIDER=ts-frontend|arkanalyzer
with ETS_FRONTEND_DIR / ARKANALYZER_DIR respectively).
"""

private class Options(args: Array<String>) {
    val input: Path = Path(args[0])
    var recursive = false
    val excludes = mutableListOf(".d.ts", "node_modules", ".test.ts", ".spec.ts")
    var maxFiles = Int.MAX_VALUE
    var projectScene = true
    var projectFrontend = false
    var classFilter: String? = null
    var methodFilter: String? = null
    var methodIds: Set<String>? = null
    var modes: List<AnalysisMode> = listOf(AnalysisMode.HYBRID_WITH_HINTS)
    var seed = 0L
    var pbtIterations = 2_000
    var targetTimeoutSec = 20
    var hintFallback = true
    val externalInputPaths = mutableListOf<Path>()
    var externalOnly = false
    var targetManifestPath: Path? = null
    var manifestOnly = false
    var outPrefix = "hybrid-report"

    init {
        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "--recursive" -> recursive = true
                "--exclude" -> excludes += args[++i]
                "--max-files" -> maxFiles = args[++i].toInt()
                "--file-scenes" -> projectScene = false
                "--project-frontend" -> projectFrontend = true
                "--class" -> classFilter = args[++i]
                "--method" -> methodFilter = args[++i]
                "--method-ids" -> methodIds = Path(args[++i]).readLines().map(String::trim).filter(String::isNotEmpty).toSet()
                "--modes" -> modes = args[++i].split(',').map { AnalysisMode.valueOf(it.trim()) }
                "--mode" -> modes = listOf(AnalysisMode.valueOf(args[++i])) // backward compat
                "--seed" -> seed = args[++i].toLong()
                "--pbt-iterations" -> pbtIterations = args[++i].toInt()
                "--target-timeout" -> targetTimeoutSec = args[++i].toInt()
                "--no-fallback" -> hintFallback = false
                "--external-inputs" -> externalInputPaths.add(Path(args[++i]))
                "--external-only" -> externalOnly = true
                "--export-target-manifest" -> targetManifestPath = Path(args[++i])
                "--manifest-only" -> manifestOnly = true
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
        .filter { method ->
            val fileName = method.signature.enclosingClass.file.fileName
            fileName.endsWith(".ts") && opts.excludes.none { fileName.contains(it) }
        }
        .filter { opts.methodFilter == null || it.name == opts.methodFilter }
        .filter { opts.methodIds == null || stableMethodId(it) in opts.methodIds.orEmpty() }
        .toList()

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(USAGE)
        exitProcess(1)
    }
    val opts = Options(args)
    if (opts.externalOnly && opts.externalInputPaths.isEmpty()) {
        println("--external-only requires at least one --external-inputs file\n$USAGE")
        exitProcess(1)
    }
    if (opts.manifestOnly && opts.targetManifestPath == null) {
        println("--manifest-only requires --export-target-manifest <path>\n$USAGE")
        exitProcess(1)
    }
    if (opts.projectFrontend && !opts.input.isDirectory()) {
        println("--project-frontend requires a directory input\n$USAGE")
        exitProcess(1)
    }
    if (opts.projectFrontend && !opts.projectScene) {
        println("--project-frontend and --file-scenes are mutually exclusive\n$USAGE")
        exitProcess(1)
    }

    val externalProviders = try {
        opts.externalInputPaths.map(ExternalCorpusInputProvider::fromPath)
    } catch (e: Throwable) {
        println("Failed to load external input corpus: ${e.message}")
        exitProcess(1)
    }

    val files = collectFiles(opts)
    println("Corpus: ${files.size} file(s)")

    val scenes: List<Pair<Path, EtsScene>>
    if (opts.projectFrontend) {
        scenes = try {
            listOf(opts.input to loadEtsProjectAutoConvert(opts.input))
        } catch (e: Throwable) {
            logger.warn { "project frontend failed for ${opts.input}: ${e.message?.take(500)}" }
            emptyList()
        }
        println("Project frontend: ${if (scenes.isEmpty()) "failed" else "loaded one TypeScript Program"}")
    } else {
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

        // Default: ONE scene over the whole corpus, so that cross-file classes
        // and free functions resolve. --file-scenes restores old isolation.
        scenes = if (opts.projectScene) {
            if (loadedFiles.isEmpty()) emptyList()
            else listOf(opts.input to EtsScene(loadedFiles.map { it.second }))
        } else {
            loadedFiles.map { (path, file) -> path to EtsScene(listOf(file)) }
        }
    }
    println(
        "Scene mode: ${when {
            opts.projectFrontend -> "native project frontend (1 scene)"
            opts.projectScene -> "combined files (1 scene)"
            else -> "per-file (${scenes.size} scenes)"
        }}"
    )

    opts.targetManifestPath?.let { path ->
        val methods = scenes.flatMap { (_, scene) -> selectMethods(scene, opts) }
        path.writeText(TargetManifest.encode(TargetManifest.fromMethods(methods)))
        println("Target manifest written to $path (${methods.distinctBy { it.signature }.size} methods)")
    }
    if (opts.manifestOnly) return

    for (mode in opts.modes) {
        val config = HybridConfig(
            mode = mode,
            seed = opts.seed,
            pbtMaxIterations = opts.pbtIterations,
            perTargetTimeout = opts.targetTimeoutSec.seconds,
            hintFallback = opts.hintFallback,
            externalInputProviders = externalProviders,
            internalPbtEnabled = !opts.externalOnly,
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
                internalPbtEnabled = !opts.externalOnly,
                externalInputProducers = externalProviders.map { it.name },
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
    val externalImported = ms.sumOf { it.pbt?.externalImported ?: 0 }
    val externalExecuted = ms.sumOf { it.pbt?.externalExecuted ?: 0 }
    val externalRejected = ms.sumOf { it.pbt?.externalRejected ?: 0 }
    val externalDeduplicated = ms.sumOf { it.pbt?.externalDeduplicated ?: 0 }
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
    val reasonTotals = mutableMapOf<String, Int>()
    for (m in ms) {
        for ((k, v) in m.pbt?.unsupportedReasons.orEmpty()) {
            reasonTotals[k] = (reasonTotals[k] ?: 0) + v
        }
    }
    if (reasonTotals.isNotEmpty()) {
        println("  top unsupported reasons:")
        reasonTotals.entries.sortedByDescending { it.value }.take(10).forEach { (k, v) ->
            println("    %6d  %s".format(v, k))
        }
    }
    if (externalImported > 0 || externalRejected > 0) {
        println(
            "  external inputs: imported=$externalImported, executed=$externalExecuted, " +
                "rejected=$externalRejected, deduplicated=$externalDeduplicated"
        )
    }
    println(
        "  pbt failures: $failures, unsupported executions: $unsupported; " +
            "symbolic targets: ${targets.size}, reached: ${targets.count { it.reached }}, " +
            "replay-confirmed: ${targets.count { it.replayConfirmed }}, " +
            "fallbacks: ${targets.count { it.fallbackUsed }}; wall: ${wallMs / 1000.0}s"
    )
}
