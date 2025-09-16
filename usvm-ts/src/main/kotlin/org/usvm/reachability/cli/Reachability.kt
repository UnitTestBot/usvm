package org.usvm.reachability.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.api.targets.TsTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reachability Analysis CLI for TypeScript code
 *
 * This tool performs reachability analysis on TypeScript projects to determine
 * which code paths can be reached under various conditions.
 */
class ReachabilityAnalyzer : CliktCommand(
    name = "reachability"
) {
    init {
        context {
            helpFormatter = {
                MordantHelpFormatter(
                    it,
                    requiredOptionMarker = "🔸",
                    showDefaultValues = true,
                    showRequiredTag = true
                )
            }
        }
    }

    // Input Options
    private val projectPath by option(
        "-p", "--project",
        help = "📁 Path to TypeScript project directory"
    ).path(mustExist = true).required()

    private val targetsFile by option(
        "-t", "--targets",
        help = "📋 JSON file with target definitions (optional - will analyze all methods if not provided)"
    ).path(mustExist = true)

    private val output by option(
        "-o", "--output",
        help = "📄 Output directory for analysis results"
    ).path().default(Path("./reachability-results"))

    // Analysis Configuration
    private val analysisMode by option(
        "-m", "--mode",
        help = "🔍 Analysis scope"
    ).enum<AnalysisMode>().default(AnalysisMode.PUBLIC_METHODS)

    private val methodFilter by option(
        "--method",
        help = "🎯 Filter methods by name pattern"
    ).multiple()

    // Solver & Performance Options
    private val solverType by option(
        "--solver",
        help = "⚙️ SMT solver"
    ).enum<SolverType>().default(SolverType.YICES)

    private val timeout by option(
        "--timeout",
        help = "⏰ Analysis timeout (seconds)"
    ).int().default(300)

    private val stepsLimit by option(
        "--steps",
        help = "👣 Max steps from last covered statement"
    ).long().default(3500L)

    private val pathStrategy by option(
        "--strategy",
        help = "🛤️ Path selection strategy"
    ).enum<PathSelectionStrategy>().default(PathSelectionStrategy.TARGETED)

    // Output Options
    private val verbose by option(
        "-v", "--verbose",
        help = "📝 Verbose output"
    ).flag()

    private val includeStatements by option(
        "--include-statements",
        help = "📍 Include statement details in output"
    ).flag()

    override fun run() {
        setupLogging()

        echo("🚀 Starting TypeScript Reachability Analysis")
        echo(
            "" +
                "┌─────────────────────────────────────────┐\n" +
                "│         USVM Reachability Tool          │\n" +
                "└─────────────────────────────────────────┘"
        )

        val startTime = System.currentTimeMillis()

        try {
            val results = performAnalysis()
            generateReports(results, startTime)

            echo("✅ Analysis completed successfully!")

        } catch (e: Exception) {
            echo("❌ Analysis failed: ${e.message}", err = true)
            if (verbose) {
                e.printStackTrace()
            }
            throw e
        }
    }

    private fun setupLogging() {
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
        }
    }

    private fun performAnalysis(): ReachabilityResults {
        echo("🔍 Loading TypeScript project...")

        // Load TypeScript project using autoConvert like in tests
        val tsFiles = findTypeScriptFiles(projectPath)
        if (tsFiles.isEmpty()) {
            throw IllegalArgumentException("No TypeScript files found in ${projectPath}")
        }

        echo("📁 Found ${tsFiles.size} TypeScript files")
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it) })
        echo("📊 Project loaded: ${scene.projectClasses.size} classes")

        // Configure machine options
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(pathStrategy),
            exceptionsPropagation = true,
            stopOnTargetsReached = true,
            timeout = timeout.seconds,
            stepsFromLastCovered = stepsLimit,
            solverType = solverType,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        val tsOptions = TsOptions()
        val machine = TsMachine(scene, machineOptions, tsOptions, machineObserver = ReachabilityObserver())

        // Find methods to analyze
        val methodsToAnalyze = findMethodsToAnalyze(scene)
        echo("🎯 Analyzing ${methodsToAnalyze.size} methods")

        // Create targets
        val targetDefinitions = if (targetsFile != null) {
            parseTargetDefinitions(targetsFile!!)
        } else {
            generateDefaultTargets(methodsToAnalyze)
        }

        val targets = createTargets(methodsToAnalyze, targetDefinitions)
        echo("📍 Created ${targets.size} reachability targets")

        // Run analysis
        echo("⚡ Running reachability analysis...")
        val states = machine.analyze(methodsToAnalyze, targets)

        // Analyze results for reachability
        val reachabilityResults = analyzeReachability(targets, states)

        return ReachabilityResults(
            methods = methodsToAnalyze,
            targets = targets,
            targetDefinitions = targetDefinitions,
            states = states,
            reachabilityResults = reachabilityResults,
            scene = scene
        )
    }

    private fun findTypeScriptFiles(projectDir: Path): List<Path> {
        return projectDir.toFile().walkTopDown()
            .filter { it.isFile && (it.extension == "ts" || it.extension == "js") }
            .map { it.toPath() }
            .toList()
    }

    private fun findMethodsToAnalyze(scene: EtsScene): List<EtsMethod> {
        val allMethods = scene.projectClasses.flatMap { it.methods }

        return if (methodFilter.isNotEmpty()) {
            allMethods.filter { method ->
                val fullName = "${method.enclosingClass?.name ?: "Unknown"}.${method.name}"
                methodFilter.any { pattern ->
                    fullName.contains(pattern, ignoreCase = true)
                }
            }
        } else {
            when (analysisMode) {
                AnalysisMode.ALL_METHODS -> allMethods
                AnalysisMode.PUBLIC_METHODS -> allMethods.filter { it.isPublic }
                AnalysisMode.ENTRY_POINTS -> allMethods.filter {
                    it.name == "main" || it.isPublic
                }
            }
        }
    }

    private fun parseTargetDefinitions(targetsFile: Path): List<TargetDefinition> {
        val content = targetsFile.readText()
        return try {
            // Simple JSON parsing for target definitions
            // Expected format: [{"type": "initial|intermediate|final", "method": "ClassName.methodName", "statement": index}]
            val lines = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("[") && !it.startsWith("]") }
                .filter { it.contains("\"type\"") }

            lines.mapNotNull { line ->
                try {
                    val typeMatch = Regex("\"type\"\\s*:\\s*\"(\\w+)\"").find(line)
                    val methodMatch = Regex("\"method\"\\s*:\\s*\"([^\"]+)\"").find(line)
                    val statementMatch = Regex("\"statement\"\\s*:\\s*(\\d+)").find(line)

                    if (typeMatch != null && methodMatch != null && statementMatch != null) {
                        val type = when (typeMatch.groupValues[1].lowercase()) {
                            "initial" -> TargetType.INITIAL
                            "intermediate" -> TargetType.INTERMEDIATE
                            "final" -> TargetType.FINAL
                            else -> return@mapNotNull null
                        }

                        TargetDefinition(
                            type = type,
                            methodName = methodMatch.groupValues[1],
                            statementIndex = statementMatch.groupValues[1].toInt()
                        )
                    } else null
                } catch (_: Exception) {
                    echo("⚠️  Warning: Could not parse target definition: $line", err = true)
                    null
                }
            }
        } catch (_: Exception) {
            echo("❌ Error parsing targets file", err = true)
            emptyList()
        }
    }

    private fun generateDefaultTargets(methods: List<EtsMethod>): List<TargetDefinition> {
        return methods.flatMap { method ->
            val statements = method.cfg.stmts
            if (statements.isEmpty()) return@flatMap emptyList()

            val targets = mutableListOf<TargetDefinition>()

            // Initial point
            targets.add(
                TargetDefinition(
                    type = TargetType.INITIAL,
                    methodName = "${method.enclosingClass?.name ?: "Unknown"}.${method.name}",
                    statementIndex = 0
                )
            )

            // Intermediate points for control flow statements
            statements.forEachIndexed { index, stmt ->
                when (stmt) {
                    is EtsIfStmt, is EtsReturnStmt -> {
                        targets.add(
                            TargetDefinition(
                                type = if (stmt == statements.last()) TargetType.FINAL else TargetType.INTERMEDIATE,
                                methodName = "${method.enclosingClass?.name ?: "Unknown"}.${method.name}",
                                statementIndex = index
                            )
                        )
                    }
                }
            }

            targets
        }
    }

    private fun createTargets(methods: List<EtsMethod>, targetDefs: List<TargetDefinition>): List<TsTarget> {
        val targets = mutableListOf<TsTarget>()
        val methodMap = methods.associateBy { "${it.enclosingClass?.name ?: "Unknown"}.${it.name}" }

        // Group targets by method to build hierarchical structure
        val targetsByMethod = targetDefs.groupBy { it.methodName }

        targetsByMethod.forEach { (methodName, methodTargets) ->
            val method = methodMap[methodName] ?: return@forEach
            val statements = method.cfg.stmts

            if (statements.isEmpty()) return@forEach

            // Find initial target
            val initialTarget = methodTargets.find { it.type == TargetType.INITIAL }
                ?.let { targetDef ->
                    if (targetDef.statementIndex < statements.size) {
                        TsReachabilityTarget.InitialPoint(statements[targetDef.statementIndex])
                    } else null
                } ?: return@forEach

            targets.add(initialTarget)

            // Build chain of intermediate and final targets
            var currentTarget: TsTarget = initialTarget
            methodTargets.filter { it.type != TargetType.INITIAL }
                .sortedBy { it.statementIndex }
                .forEach { targetDef ->
                    if (targetDef.statementIndex < statements.size) {
                        val stmt = statements[targetDef.statementIndex]
                        val target = when (targetDef.type) {
                            TargetType.INTERMEDIATE -> TsReachabilityTarget.IntermediatePoint(stmt)
                            TargetType.FINAL -> TsReachabilityTarget.FinalPoint(stmt)
                            else -> return@forEach
                        }
                        currentTarget = currentTarget.addChild(target)
                    }
                }
        }

        return targets
    }

    private fun analyzeReachability(
        targets: List<TsTarget>,
        states: List<TsState>,
    ): List<TargetReachabilityResult> {
        val results = mutableListOf<TargetReachabilityResult>()

        // Get all reached statements across all execution paths
        val allReachedStatements = states.flatMap { it.pathNode.allStatements }.toSet()

        targets.forEach { target ->
            val targetLocation = target.location
            if (targetLocation != null) {
                val reachabilityStatus = when {
                    targetLocation in allReachedStatements -> ReachabilityStatus.REACHABLE
                    states.isEmpty() -> ReachabilityStatus.UNKNOWN
                    else -> ReachabilityStatus.UNREACHABLE
                }

                val executionPaths = if (reachabilityStatus == ReachabilityStatus.REACHABLE) {
                    states.filter { targetLocation in it.pathNode.allStatements }
                        .map { state ->
                            ExecutionPath(
                                statements = state.pathNode.allStatements.toList(),
                                pathConditions = emptyList() // TODO: Extract path conditions
                            )
                        }
                } else {
                    emptyList()
                }

                results.add(
                    TargetReachabilityResult(
                        target = target,
                        status = reachabilityStatus,
                        executionPaths = executionPaths
                    )
                )
            }
        }

        return results
    }

    private fun generateReports(results: ReachabilityResults, startTime: Long) {
        echo("📊 Generating analysis reports...")

        output.createDirectories()
        val duration = (System.currentTimeMillis() - startTime) / 1000.0

        generateSummaryReport(results, duration)
        generateDetailedReport(results, duration)

        printSummaryToConsole(results, duration)
    }

    private fun generateSummaryReport(results: ReachabilityResults, duration: Double) {
        val reportFile = output / "reachability_summary.txt"
        reportFile.writeText(buildString {
            appendLine("🎯 REACHABILITY ANALYSIS SUMMARY")
            appendLine("=".repeat(50))
            appendLine("⏱️  Duration: ${String.format("%.2f", duration)}s")
            appendLine("🔍 Methods analyzed: ${results.methods.size}")
            appendLine("📍 Targets analyzed: ${results.reachabilityResults.size}")

            val statusCounts = results.reachabilityResults.groupingBy { it.status }.eachCount()
            appendLine("✅ Reachable: ${statusCounts[ReachabilityStatus.REACHABLE] ?: 0}")
            appendLine("❌ Unreachable: ${statusCounts[ReachabilityStatus.UNREACHABLE] ?: 0}")
            appendLine("❓ Unknown: ${statusCounts[ReachabilityStatus.UNKNOWN] ?: 0}")

            appendLine("\n📈 DETAILED RESULTS")
            appendLine("-".repeat(30))

            results.reachabilityResults.forEach { result ->
                val targetType = when (result.target) {
                    is TsReachabilityTarget.InitialPoint -> "INITIAL"
                    is TsReachabilityTarget.IntermediatePoint -> "INTERMEDIATE"
                    is TsReachabilityTarget.FinalPoint -> "FINAL"
                    else -> "UNKNOWN"
                }

                appendLine("Target: $targetType at ${result.target.location?.javaClass?.simpleName}")
                appendLine("  Status: ${result.status}")
                if (result.executionPaths.isNotEmpty()) {
                    appendLine("  Paths found: ${result.executionPaths.size}")
                }
                appendLine()
            }
        })

        echo("📄 Summary saved to: ${reportFile.relativeTo(Path("."))}")
    }

    private fun generateDetailedReport(results: ReachabilityResults, duration: Double) {
        val reportFile = output / "reachability_detailed.md"
        reportFile.writeText(buildString {
            appendLine("# 🎯 TypeScript Reachability Analysis - Detailed Report")
            appendLine()
            appendLine("**Analysis Duration:** ${String.format("%.2f", duration)}s")
            appendLine("**Methods Analyzed:** ${results.methods.size}")
            appendLine("**Targets Analyzed:** ${results.reachabilityResults.size}")
            appendLine()

            val statusCounts = results.reachabilityResults.groupingBy { it.status }.eachCount()
            appendLine("## 📊 Reachability Summary")
            appendLine("- ✅ **Reachable:** ${statusCounts[ReachabilityStatus.REACHABLE] ?: 0}")
            appendLine("- ❌ **Unreachable:** ${statusCounts[ReachabilityStatus.UNREACHABLE] ?: 0}")
            appendLine("- ❓ **Unknown:** ${statusCounts[ReachabilityStatus.UNKNOWN] ?: 0}")
            appendLine()

            appendLine("## 🔍 Target Analysis Results")
            appendLine()

            results.reachabilityResults.forEach { result ->
                val targetType = when (result.target) {
                    is TsReachabilityTarget.InitialPoint -> "Initial Point"
                    is TsReachabilityTarget.IntermediatePoint -> "Intermediate Point"
                    is TsReachabilityTarget.FinalPoint -> "Final Point"
                    else -> "Unknown Target"
                }

                val statusIcon = when (result.status) {
                    ReachabilityStatus.REACHABLE -> "✅"
                    ReachabilityStatus.UNREACHABLE -> "❌"
                    ReachabilityStatus.UNKNOWN -> "❓"
                }

                appendLine("### $statusIcon $targetType")
                appendLine("**Location:** ${result.target.location?.javaClass?.simpleName ?: "Unknown"}")
                appendLine("**Status:** ${result.status}")

                if (result.executionPaths.isNotEmpty()) {
                    appendLine("**Execution Paths Found:** ${result.executionPaths.size}")

                    if (includeStatements) {
                        result.executionPaths.forEachIndexed { pathIndex, path ->
                            appendLine("#### Path ${pathIndex + 1}")
                            appendLine("Statements in execution path:")
                            path.statements.forEachIndexed { stmtIndex, stmt ->
                                appendLine(
                                    "${stmtIndex + 1}. ${stmt.javaClass.simpleName}: `${
                                        stmt.toString().take(60)
                                    }...`"
                                )
                            }
                            appendLine()
                        }
                    }
                }
                appendLine()
            }
        })

        echo("📄 Detailed report saved to: ${reportFile.relativeTo(Path("."))}")
    }

    private fun printSummaryToConsole(results: ReachabilityResults, duration: Double) {
        echo("")
        echo("🎉 Analysis Complete!")
        echo("⏱️ Duration: ${String.format("%.2f", duration)}s")
        echo("🔍 Methods: ${results.methods.size}")
        echo("📍 Targets: ${results.reachabilityResults.size}")

        val statusCounts = results.reachabilityResults.groupingBy { it.status }.eachCount()
        echo("✅ Reachable: ${statusCounts[ReachabilityStatus.REACHABLE] ?: 0}")
        echo("❌ Unreachable: ${statusCounts[ReachabilityStatus.UNREACHABLE] ?: 0}")
        echo("❓ Unknown: ${statusCounts[ReachabilityStatus.UNKNOWN] ?: 0}")
        echo("📁 Reports saved to: ${output.relativeTo(Path("."))}")
    }
}

// Enums and data classes
enum class AnalysisMode {
    ALL_METHODS,
    PUBLIC_METHODS,
    ENTRY_POINTS
}

enum class TargetType {
    INITIAL,
    INTERMEDIATE,
    FINAL
}

enum class ReachabilityStatus {
    REACHABLE,     // Confirmed reachable with execution path
    UNREACHABLE,   // Confirmed unreachable
    UNKNOWN        // Could not determine (timeout/approximation/error)
}

data class TargetDefinition(
    val type: TargetType,
    val methodName: String,
    val statementIndex: Int,
)

data class ExecutionPath(
    val statements: List<EtsStmt>,
    val pathConditions: List<String>, // Simplified - would contain actual conditions
)

data class TargetReachabilityResult(
    val target: TsTarget,
    val status: ReachabilityStatus,
    val executionPaths: List<ExecutionPath>,
)

data class ReachabilityResults(
    val methods: List<EtsMethod>,
    val targets: List<TsTarget>,
    val targetDefinitions: List<TargetDefinition>,
    val states: List<TsState>,
    val reachabilityResults: List<TargetReachabilityResult>,
    val scene: EtsScene,
)

fun main(args: Array<String>) {
    ReachabilityAnalyzer().main(args)
}
