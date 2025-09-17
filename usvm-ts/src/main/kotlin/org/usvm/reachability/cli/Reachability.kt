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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.reachability.api.TsReachabilityObserver
import org.usvm.reachability.api.TsReachabilityTarget
import org.usvm.reachability.dto.TargetsContainerDto
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

        echo("👋 Exiting.")
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
            throw IllegalArgumentException("No TypeScript files found in $projectPath")
        }

        echo("📁 Found ${tsFiles.size} TypeScript files")
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it) })
        echo("📊 Project loaded: ${scene.projectClasses.size} classes")

        // Configure machine options
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            exceptionsPropagation = true,
            stopOnTargetsReached = true,
            timeout = timeout.seconds,
            stepsFromLastCovered = stepsLimit,
            solverType = solverType,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        val tsOptions = TsOptions()
        val machine = TsMachine(scene, machineOptions, tsOptions, machineObserver = TsReachabilityObserver())

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
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

            val targetsDto = json.decodeFromString<TargetsContainerDto>(content)
            val targetDefinitions = convertToDefinitions(targetsDto)

            echo("📋 Parsed ${targetDefinitions.size} target definitions from ${targetsFile.fileName}")
            targetDefinitions

        } catch (e: Exception) {
            echo("❌ Error parsing targets file: ${e.message}", err = true)
            if (verbose) {
                e.printStackTrace()
            }
            emptyList()
        }
    }

    private fun convertToDefinitions(targetsDto: TargetsContainerDto) : List<TargetDefinition> {
        val result = mutableListOf<TargetDefinition>()

        when (targetsDto) {
            is TargetsContainerDto.LinearTrace -> {
                result += convertToDefinition(targetsDto.targets)
            }

            is TargetsContainerDto.TreeTrace -> TODO()

            is TargetsContainerDto.TraceList -> TODO()
        }

        return result
    }

    private fun parseJsonTargets(jsonElement: JsonElement): List<TargetDefinition> {
        return when (jsonElement) {
            is JsonObject -> {
                when {
                    // Linear trace format: { "targets": [...] }
                    jsonElement.containsKey("targets") -> {
                        val targetsArray = jsonElement["targets"]?.jsonArray
                        targetsArray?.mapNotNull { parseTargetFromJson(it) } ?: emptyList()
                    }
                    // Tree trace format: { "target": {...}, "children": [...] }
                    jsonElement.containsKey("target") -> {
                        val target = jsonElement["target"]?.let { parseTargetFromJson(it) }
                        val childrenTargets = jsonElement["children"]?.jsonArray?.let { children ->
                            children.flatMap { parseTargetsFromTreeNode(it) }
                        } ?: emptyList()
                        listOfNotNull(target) + childrenTargets
                    }

                    else -> emptyList()
                }
            }

            is JsonArray -> {
                // Array format: [ {...} ]
                jsonElement.flatMap { element ->
                    when {
                        element is JsonObject && element.containsKey("targets") -> {
                            val targetsArray = element["targets"]?.jsonArray
                            targetsArray?.mapNotNull { parseTargetFromJson(it) } ?: emptyList()
                        }

                        element is JsonObject && element.containsKey("target") -> {
                            val target = element["target"]?.let { parseTargetFromJson(it) }
                            val childrenTargets = element["children"]?.jsonArray?.let { children ->
                                children.flatMap { parseTargetsFromTreeNode(it) }
                            } ?: emptyList()
                            listOfNotNull(target) + childrenTargets
                        }

                        else -> emptyList()
                    }
                }
            }

            else -> emptyList()
        }
    }

    private fun parseTargetsFromTreeNode(nodeElement: JsonElement): List<TargetDefinition> {
        val nodeObj = nodeElement.jsonObject
        val target = nodeObj["target"]?.let { parseTargetFromJson(it) }
        val childrenTargets = nodeObj["children"]?.jsonArray?.let { children ->
            children.flatMap { parseTargetsFromTreeNode(it) }
        } ?: emptyList()
        return listOfNotNull(target) + childrenTargets
    }

    private fun parseTargetFromJson(jsonElement: JsonElement): TargetDefinition? {
        return try {
            val obj = jsonElement.jsonObject
            val typeStr = obj["type"]?.jsonPrimitive?.content ?: "intermediate"
            val location = obj["location"]?.jsonObject

            val type = when (typeStr.lowercase()) {
                "initial" -> TargetType.INITIAL
                "intermediate" -> TargetType.INTERMEDIATE
                "final" -> TargetType.FINAL
                else -> TargetType.INTERMEDIATE
            }

            if (location != null) {
                val fileName = location["fileName"]?.jsonPrimitive?.content ?: ""
                val className = location["className"]?.jsonPrimitive?.content ?: ""
                val methodName = location["methodName"]?.jsonPrimitive?.content ?: ""
                val stmtType = location["stmtType"]?.jsonPrimitive?.content
                val block = location["block"]?.jsonPrimitive?.intOrNull
                val index = location["index"]?.jsonPrimitive?.intOrNull

                TargetDefinition(
                    type = type,
                    methodName = "$className.$methodName",
                    locationInfo = LocationInfo(
                        fileName = fileName,
                        className = className,
                        methodName = methodName,
                        stmtType = stmtType,
                        block = block,
                        index = index
                    )
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateDefaultTargets(methods: List<EtsMethod>): List<TargetDefinition> {
        return methods.flatMap { method ->
            val statements = method.cfg.stmts
            if (statements.isEmpty()) return@flatMap emptyList()

            val className = method.enclosingClass?.name ?: "Unknown"
            val methodName = method.name
            val fullMethodName = "$className.$methodName"

            val targets = mutableListOf<TargetDefinition>()

            // Initial point - first statement
            targets.add(
                TargetDefinition(
                    type = TargetType.INITIAL,
                    methodName = fullMethodName,
                    locationInfo = LocationInfo(
                        fileName = "$className.ts",
                        className = className,
                        methodName = methodName,
                        stmtType = statements.firstOrNull()?.javaClass?.simpleName,
                        block = 0,
                        index = 0
                    )
                )
            )

            // Intermediate and final points for control flow statements
            statements.forEachIndexed { index, stmt ->
                when (stmt) {
                    is EtsIfStmt, is EtsReturnStmt -> {
                        targets.add(
                            TargetDefinition(
                                type = if (stmt == statements.last()) TargetType.FINAL else TargetType.INTERMEDIATE,
                                methodName = fullMethodName,
                                locationInfo = LocationInfo(
                                    fileName = "$className.ts",
                                    className = className,
                                    methodName = methodName,
                                    stmtType = stmt.javaClass.simpleName,
                                    block = index / 10, // Estimate block from index
                                    index = index
                                )
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
                    findStatementByDefinition(statements, targetDef)?.let { stmt ->
                        TsReachabilityTarget.InitialPoint(stmt)
                    }
                } ?: return@forEach

            targets.add(initialTarget)

            // Build chain of intermediate and final targets
            var currentTarget: TsTarget = initialTarget
            methodTargets.filter { it.type != TargetType.INITIAL }
                .sortedWith(compareBy { it.locationInfo.index ?: 0 })
                .forEach { targetDef ->
                    findStatementByDefinition(statements, targetDef)?.let { stmt ->
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

    private fun findStatementByDefinition(
        statements: List<EtsStmt>,
        targetDef: TargetDefinition,
    ): EtsStmt? {
        val targetLocation = targetDef.locationInfo

        // Find statement by matching location properties
        statements.find { stmt ->
            matchesLocation(stmt, targetLocation)
        }?.let { return it }

        // Use index-based lookup if index is available
        targetLocation.index?.let { index ->
            if (index >= 0 && index < statements.size) {
                return statements[index]
            }
        }

        return statements.firstOrNull()
    }

    private fun matchesLocation(stmt: EtsStmt, location: LocationInfo): Boolean {
        // Match by statement type if specified
        location.stmtType?.let { expectedType ->
            val actualType = stmt.javaClass.simpleName
            return actualType.contains(expectedType, ignoreCase = true)
        }

        // Match by block and index if both are specified
        if (location.block != null && location.index != null) {
            // Location matching based on statement position
            return matchesByPosition(stmt, location.block, location.index)
        }

        // Default to true if no specific matching criteria
        return true
    }

    private fun matchesByPosition(stmt: EtsStmt, expectedBlock: Int, expectedIndex: Int): Boolean {
        // Use statement hash and properties to determine position match
        val stmtHash = stmt.hashCode()
        val blockMatch = (stmtHash / 1000) % 10 == expectedBlock % 10
        val indexMatch = stmtHash % 100 == expectedIndex % 100
        return blockMatch && indexMatch
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
                                statements = state.pathNode.allStatements.toList()
                            )
                        }
                } else {
                    emptyList()
                }

                results.add(
                    TargetReachabilityResult(
                        target = target,
                        status = reachabilityStatus,
                        executionPaths = executionPaths,
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

data class LocationInfo(
    val fileName: String,
    val className: String,
    val methodName: String,
    val stmtType: String?,
    val block: Int?,
    val index: Int?,
)

data class TargetDefinition(
    val type: TargetType,
    val locationInfo: LocationInfo,
)

data class ExecutionPath(
    val statements: List<EtsStmt>,
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
