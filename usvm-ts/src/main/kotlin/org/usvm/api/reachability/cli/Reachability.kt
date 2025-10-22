package org.usvm.api.reachability.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.jacodb.ets.utils.loadEtsProjectFromMultipleIR
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.reachability.TsReachabilityObserver
import org.usvm.api.reachability.TsReachabilityTarget
import org.usvm.api.reachability.dto.AnalysisReportDto
import org.usvm.api.reachability.dto.AnalysisResultDto
import org.usvm.api.reachability.dto.AnalysisSummaryDto
import org.usvm.api.reachability.dto.LocationDto
import org.usvm.api.reachability.dto.ReachabilityStatusDto
import org.usvm.api.reachability.dto.TargetDto
import org.usvm.api.reachability.dto.TargetTreeNodeDto
import org.usvm.api.reachability.dto.TargetTypeDto
import org.usvm.api.reachability.dto.TargetsContainerDto
import org.usvm.api.reachability.dto.extractTargetTraces
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.util.countLeaves
import org.usvm.util.getLeaves
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reachability Analysis CLI for TypeScript code
 *
 * This tool performs reachability analysis on TypeScript projects to determine
 * which code paths can be reached under various conditions.
 *
 * Supports both source code analysis (auto-conversion) and direct IR loading.
 */
class ReachabilityAnalyzer : CliktCommand(
    name = "reachability"
) {
    init {
        context {
            helpFormatter = {
                MordantHelpFormatter(
                    it,
                    requiredOptionMarker = "*",
                    showDefaultValues = true,
                    showRequiredTag = true
                )
            }
        }
    }

    // Input Options
    private val projectIrPaths by option(
        "-i", "--input-ir",
        help = "📂 Path to directory with IR JSON files"
    ).path(mustExist = true).multiple()

    private val sdkIrPaths by option(
        "--sdk-ir",
        help = "📚 Path to SDK directory with IR JSON files"
    ).path(mustExist = true).multiple()

    private val projectPath by option(
        "-p", "--project",
        help = "📁 Path to TypeScript project directory"
    ).path(mustExist = true)

    private val sdkPaths by option(
        "--sdk",
        help = "📚 Path to SDK directory with TypeScript declaration files"
    ).path(mustExist = true).multiple()

    private val useArkAnalyzerTypeInference by option(
        "--type-inference",
        help = "🔬 Use Ark Analyzer type inference (1=enabled, <n>=multi-pass, default=no)"
    ).int()

    private val targetsFile by option(
        "-t", "--targets",
        help = "📋 JSON file with target definitions (optional - will analyze all methods if not provided)"
    ).path(mustExist = true)

    private val output by option(
        "-o", "--output",
        help = "📄 Output directory for analysis results"
    ).path()

    // Analysis Configuration
    private val analysisMode by option(
        "-m", "--mode",
        help = "🔍 Analysis scope"
    ).enum<AnalysisMode>().default(AnalysisMode.ALL_METHODS)

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
        // Validate input options
        if (projectPath == null && projectIrPaths.isEmpty()) {
            echo("❌ Error: Either --project or --input-ir must be specified", err = true)
            echo("Use --help for usage information")
            throw IllegalArgumentException("No input specified")
        }

        if (projectPath != null && projectIrPaths.isNotEmpty()) {
            echo("❌ Error: Cannot specify both --project and --input-ir", err = true)
            echo("Use --help for usage information")
            throw IllegalArgumentException("Conflicting input options")
        }

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
        val scene = if (projectPath != null) {
            // Source mode: Load TypeScript project using autoConvert with SDK support
            echo("🔍 Loading TypeScript project from source...")
            val project = loadEtsProjectAutoConvert(
                projectPath = projectPath!!,
                useArkAnalyzerTypeInference = useArkAnalyzerTypeInference
            )
            echo("📁 Loaded ${project.projectFiles.size} project files")

            // Handle SDK files in source mode
            val sdkFiles = if (sdkPaths.isNotEmpty()) {
                echo("📚 Processing SDK directories: ${sdkPaths.joinToString(", ")}")
                sdkPaths.flatMap { sdkPath ->
                    val sdkProject = loadEtsProjectAutoConvert(
                        projectPath = sdkPath,
                        useArkAnalyzerTypeInference = null  // SDK always uses null
                    )
                    echo("📚 Loaded ${sdkProject.projectFiles.size} SDK files from $sdkPath")
                    sdkProject.projectFiles
                }
            } else {
                emptyList()
            }

            val loadedScene = EtsScene(project.projectFiles, sdkFiles)
            echo("📊 Project loaded: ${loadedScene.projectClasses.size} classes")
            loadedScene
        } else {
            // IR mode: Load project from IR directories with SDK support
            echo("🔍 Loading TypeScript project from IR...")
            echo("📂 Input directories: ${projectIrPaths.joinToString(", ")}")

            // Combine both SDK IR paths and convert source SDK paths to IR if needed
            val allSdkIrPaths = sdkIrPaths.toMutableList()

            if (sdkPaths.isNotEmpty()) {
                echo("📚 Converting source SDK directories to IR: ${sdkPaths.joinToString(", ")}")
                // Here we would need to convert source SDK files to IR format
                // For now, we'll log this requirement
                echo("⚠️ Note: Source SDK conversion to IR not yet implemented. Use --sdk-ir for IR-based SDKs.")
            }

            if (sdkIrPaths.isNotEmpty()) {
                echo("📚 SDK IR directories: ${sdkIrPaths.joinToString(", ")}")
            }

            val project = loadEtsProjectFromMultipleIR(projectIrPaths, allSdkIrPaths)
            val loadedScene = EtsScene(project.projectFiles)
            echo("📊 Project loaded: ${loadedScene.projectClasses.size} classes")
            loadedScene
        }

        // Find methods to analyze
        val methodsToAnalyze = findMethodsToAnalyze(scene)
        echo("🎯 Analyzing ${methodsToAnalyze.size} methods")

        // Prepare targets
        val targets = if (targetsFile != null) {
            val targetTraces = parseTargetDefinitions(targetsFile!!)
            val targets = createTargetsFromTraces(methodsToAnalyze, targetTraces)
            echo("📍 Created ${targets.sumOf { it.countLeaves() }} reachability target trees from ${targetTraces.size} traces")
            targets
        } else {
            val targets = generateDefaultTargets(methodsToAnalyze)
            echo("📍 Generated ${targets.sumOf { it.countLeaves() }} default reachability targets")
            targets
        }

        // Configure machine options
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            exceptionsPropagation = true,
            stopOnTargetsReached = true,
            timeout = timeout.seconds,
            stepsFromLastCovered = stepsLimit,
            stopOnCoverage = 200, // disable parameter
            solverType = solverType,
            solverTimeout = 1.seconds,
            typeOperationsTimeout = Duration.INFINITE,
            stateCollectionStrategy = StateCollectionStrategy.REACHED_TARGET,
        )
        val tsOptions = TsOptions(isTargetedModeEnabled = true)

        // Run analysis
        echo("⚡ Running reachability analysis...")


        val (collectedStates, isFinished) = TsMachine(
            scene,
            options,
            tsOptions,
            machineObserver = TsReachabilityObserver()
        ).use { machine ->
            machine.analyze(methodsToAnalyze, targets) to machine.isFinished
        }

        // Analyze results for reachability
        val reachabilityResults = analyzeReachability(targets, collectedStates, isFinished)

        return ReachabilityResults(
            methods = methodsToAnalyze,
            targets = targets,
            states = collectedStates,
            reachabilityResults = reachabilityResults,
            scene = scene
        )
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

    private fun parseTargetDefinitions(targetsFile: Path): List<TargetTrace> {
        val content = targetsFile.readText()
        return try {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

            val container = json.decodeFromString<TargetsContainerDto>(content)
            val traces = extractTargetTraces(container)

            echo("📋 Parsed ${traces.size} target traces from ${targetsFile.fileName}")
            traces

        } catch (e: Exception) {
            echo("❌ Error parsing targets file: ${e.message}", err = true)
            if (verbose) {
                e.printStackTrace()
            }
            emptyList()
        }
    }

    // TODO: remove
    private fun buildLinearTrace(targets: List<TargetDto>): TargetTreeNodeDto? {
        if (targets.isEmpty()) return null

        var current: TargetTreeNodeDto? = null

        for (target in targets.asReversed()) {
            current = TargetTreeNodeDto(target, children = listOfNotNull(current))
        }

        return current
    }

    private fun generateDefaultTargets(methods: List<EtsMethod>): List<TsReachabilityTarget> {
        return methods.mapNotNull { method ->
            val statements = method.cfg.stmts
            if (statements.isEmpty()) return@mapNotNull null

            // Build target tree following CFG paths from start to return statements
            buildTargetTree(method, statements.first())
        }
    }

    private fun buildTargetTree(method: EtsMethod, startStmt: EtsStmt): TsReachabilityTarget {
        val visited = mutableSetOf<EtsStmt>()

        fun findRelevantTargets(stmt: EtsStmt): List<TsReachabilityTarget> {
            if (stmt in visited) return emptyList()
            visited.add(stmt)

            val targets = mutableListOf<TsReachabilityTarget>()

            when (stmt) {
                startStmt -> {
                    // First statement is the initial point
                    val initialTarget = TsReachabilityTarget.InitialPoint(stmt)
                    // Add children by traversing successors
                    val successors = method.cfg.successors(stmt)
                    successors.forEach { successor ->
                        findRelevantTargets(successor).forEach { childTarget ->
                            initialTarget.addChild(childTarget)
                        }
                    }
                    targets.add(initialTarget)
                }

                is EtsReturnStmt -> {
                    // Return statements are always final points with no children
                    targets.add(TsReachabilityTarget.FinalPoint(stmt))
                }

                is EtsIfStmt -> {
                    // If statements are intermediate points that can branch
                    val ifTarget = TsReachabilityTarget.IntermediatePoint(stmt)
                    // Add children by traversing successors
                    val successors = method.cfg.successors(stmt)
                    successors.forEach { successor ->
                        findRelevantTargets(successor).forEach { childTarget ->
                            ifTarget.addChild(childTarget)
                        }
                    }
                    targets.add(ifTarget)
                }

                else -> {
                    // Skip other statements - traverse through them to find relevant ones
                    val successors = method.cfg.successors(stmt)
                    successors.forEach { successor ->
                        targets.addAll(findRelevantTargets(successor))
                    }
                }
            }

            visited.remove(stmt) // Allow revisiting in different paths
            return targets
        }

        return findRelevantTargets(startStmt).first()
    }

    private fun createTargetsFromTraces(
        methods: List<EtsMethod>,
        targetTraces: List<TargetTrace>,
    ): List<TsReachabilityTarget> {
        val targets = mutableListOf<TsReachabilityTarget>()
        val methodMap = methods.associateBy {
            val enclosingClass = it.enclosingClass
            val fileName = enclosingClass?.declaringFile?.name ?: "Unknown"
            val className = enclosingClass?.name ?: "Unknown"
            "$fileName:$className.${it.name}"
        }

        targetTraces.forEach { trace ->
            val root = when (trace) {
                is TargetTrace.Tree -> trace.root
                is TargetTrace.Linear -> buildLinearTrace(trace.targets)!!
            }

            // Resolve the root target and build the hierarchy using addChild
            val rootTarget = resolveTarget(root, methodMap)
            if (rootTarget != null) {
                targets.add(rootTarget)
            }
        }

        return targets
    }

    private fun resolveTarget(node: TargetTreeNodeDto, methodMap: Map<String, EtsMethod>): TsReachabilityTarget? {
        // First, resolve the current node to a TsReachabilityTarget
        val targetLocation = node.target.location
        val methodName = "${targetLocation.fileName}:${targetLocation.className}.${targetLocation.methodName}"
        val statements = methodMap[methodName]?.cfg?.stmts ?: return null
        val stmt = findStatementByTarget(statements, node.target) ?: return null
        if (verbose) {
            echo("Resolved target ${node.target} to statement $stmt")
        }

        val currentTarget: TsReachabilityTarget = when (node.target.type) {
            TargetTypeDto.INITIAL -> TsReachabilityTarget.InitialPoint(stmt)
            TargetTypeDto.INTERMEDIATE -> TsReachabilityTarget.IntermediatePoint(stmt)
            TargetTypeDto.FINAL -> TsReachabilityTarget.FinalPoint(stmt)
        }

        // Add all children to build the hierarchical structure
        node.children.forEach { childNode ->
            val childTarget = resolveTarget(childNode, methodMap)
            if (childTarget != null) {
                currentTarget.addChild(childTarget)
            }
        }

        return currentTarget
    }

    private fun findStatementByTarget(
        statements: List<EtsStmt>,
        target: TargetDto,
    ): EtsStmt? {
        // Find statement by matching location
        for (stmt in statements) {
            // TODO: handle 'target.stmtType'
            if (matchesLocation(stmt, target.location)) {
                return stmt
            }
        }
        // Return the first statement as a fallback
        return statements.firstOrNull()
    }

    private fun matchesLocation(stmt: EtsStmt, location: LocationDto): Boolean {
        // If both block and index are specified, match by both (using original DTO block/stmt indices)
        if (location.block != null && location.index != null) {
            if (stmt.location.blockDtoIndex == location.block && stmt.location.stmtDtoIndex == location.index) {
                return true
            }
        }
        // EXTRA: If only the index is specified, match by index only (using the normal stmt index in linear CFG)
        if (location.block == null && location.index != null) {
            if (stmt.location.index == location.index) {
                return true
            }
        }
        // If only the block index is specified, match by the first instruction of the block
        if (location.block != null && location.index == null) {
            if (stmt.location.blockDtoIndex == location.block && stmt.location.stmtDtoIndex == 0) {
                return true
            }
        }
        return false
    }

    private fun analyzeReachability(
        targets: List<TsReachabilityTarget>,
        states: List<TsState>,
        isFinished: Boolean,
    ): List<TargetReachabilityResult> {
        val results = mutableListOf<TargetReachabilityResult>()

        // Get all reached terminal targets across all states
        val allReachedTerminalTargets = states.flatMapTo(hashSetOf()) { it.targets.reachedTerminal }

        // For each target tree, analyze all leaf targets (terminal targets)
        targets.forEach { target ->
            val targetStartTime = System.currentTimeMillis()

            try {
                // Get all leaf targets from this target tree
                val leafTargets = target.getLeaves()

                // Check if any leaf target in this tree was reached
                val reachedLeafTargets = leafTargets.filter { it in allReachedTerminalTargets }

                val reachabilityStatus = when {
                    reachedLeafTargets.isNotEmpty() -> ReachabilityStatus.REACHABLE
                    isFinished -> ReachabilityStatus.UNREACHABLE
                    else -> ReachabilityStatus.UNKNOWN
                }

                val executionPaths = if (reachabilityStatus == ReachabilityStatus.REACHABLE) {
                    // Find states that reached any of the leaf targets in this tree
                    states.filter { state ->
                        reachedLeafTargets.any { leafTarget -> leafTarget in state.targets.reachedTerminal }
                    }.map { state ->
                        ExecutionPath(
                            statements = state.pathNode.allStatements.toList()
                        )
                    }
                } else {
                    emptyList()
                }

                val targetEndTime = System.currentTimeMillis()
                val executionTimeMs = targetEndTime - targetStartTime

                results.add(
                    TargetReachabilityResult(
                        target = target,
                        status = reachabilityStatus,
                        executionPaths = executionPaths,
                        executionTimeMs = executionTimeMs,
                    )
                )
            } catch (e: Exception) {
                val targetEndTime = System.currentTimeMillis()
                val executionTimeMs = targetEndTime - targetStartTime

                results.add(
                    TargetReachabilityResult(
                        target = target,
                        status = ReachabilityStatus.UNKNOWN,
                        executionPaths = emptyList(),
                        executionTimeMs = executionTimeMs,
                        errorMessage = "Analysis error: ${e.message}",
                    )
                )
            }
        }

        return results
    }

    private fun generateReports(results: ReachabilityResults, startTime: Long) {
        echo("📊 Generating analysis reports...")

        val duration = (System.currentTimeMillis() - startTime) / 1000.0 // in seconds

        if (output != null) {
            output!!.createDirectories()
            generateJsonReport(output!!, results, duration)
            generateSummaryReport(output!!, results, duration)
        } else {
            echo("⚠️ Output directory not specified, skipping report generation")
        }

        printSummaryToConsole(results, duration)
    }

    private fun generateSummaryReport(
        output: Path,
        results: ReachabilityResults,
        duration: Double,
    ) {
        val reportFile = output / "reachability_summary.md"
        reportFile.writeText(buildString {
            appendLine("# 🎯 TypeScript Reachability Analysis Summary")
            appendLine()

            // Analysis Overview
            appendLine("## 📊 Analysis Overview")
            appendLine("- **Analysis Duration:** ${String.format("%.2f", duration)}s")
            appendLine("- **Methods Analyzed:** ${results.methods.size}")
            appendLine("- **Targets Analyzed:** ${results.reachabilityResults.size}")
            appendLine()

            // Reachability Summary
            val statusCounts = results.reachabilityResults.groupingBy { it.status }.eachCount()
            val totalExecutionTimeMs = results.reachabilityResults.sumOf { it.executionTimeMs }

            appendLine("## 📈 Reachability Results")
            appendLine("- ✅ **Reachable:** ${statusCounts[ReachabilityStatus.REACHABLE] ?: 0}")
            appendLine("- ❌ **Unreachable:** ${statusCounts[ReachabilityStatus.UNREACHABLE] ?: 0}")
            appendLine("- ❓ **Unknown:** ${statusCounts[ReachabilityStatus.UNKNOWN] ?: 0}")
            appendLine("- ⏱️ **Total Target Analysis Time:** ${totalExecutionTimeMs}ms")
            appendLine()

            // Methods Analyzed
            appendLine("## 🔍 Methods Analyzed")
            results.methods.forEach { method ->
                val className = method.enclosingClass?.name ?: "Unknown"
                appendLine("- `$className.${method.name}`")
            }
            appendLine()

            // Detailed Target Results
            appendLine("## 🎯 Detailed Target Analysis")
            appendLine()

            results.reachabilityResults.forEachIndexed { index, result ->
                val targetType = when (result.target) {
                    is TsReachabilityTarget.InitialPoint -> "Initial Point"
                    is TsReachabilityTarget.IntermediatePoint -> "Intermediate Point"
                    is TsReachabilityTarget.FinalPoint -> "Final Point"
                }

                val statusIcon = when (result.status) {
                    ReachabilityStatus.REACHABLE -> "✅"
                    ReachabilityStatus.UNREACHABLE -> "❌"
                    ReachabilityStatus.UNKNOWN -> "❓"
                }

                appendLine("### ${index + 1}. $statusIcon $targetType")
                appendLine("- **Target ID:** `${generateTargetId(result.target)}`")
                appendLine("- **Location:** ${result.target.location.location}")
                appendLine("- **Status:** ${result.status}")
                appendLine("- **Analysis Time:** ${result.executionTimeMs}ms")

                if (result.errorMessage != null) {
                    appendLine("- **Error:** ${result.errorMessage}")
                }

                if (result.executionPaths.isNotEmpty()) {
                    appendLine("- **Execution Paths Found:** ${result.executionPaths.size}")

                    if (includeStatements) {
                        result.executionPaths.forEachIndexed { pathIndex, path ->
                            appendLine()
                            appendLine("#### Path ${pathIndex + 1} (${path.statements.size} statements)")
                            appendLine("```")
                            path.statements.forEachIndexed { stmtIndex, stmt ->
                                appendLine(
                                    "${stmtIndex + 1}. ${stmt.javaClass.simpleName}: ${
                                        stmt.toString().take(80)
                                    }"
                                )
                            }
                            appendLine("```")
                        }
                    }
                }
                appendLine()
            }

            // Performance Summary
            if (results.reachabilityResults.isNotEmpty()) {
                appendLine("## ⚡ Performance Summary")
                val avgTimePerTarget = totalExecutionTimeMs.toDouble() / results.reachabilityResults.size
                val maxTime = results.reachabilityResults.maxOfOrNull { it.executionTimeMs } ?: 0L
                val minTime = results.reachabilityResults.minOfOrNull { it.executionTimeMs } ?: 0L

                appendLine("- **Average time per target:** ${String.format("%.2f", avgTimePerTarget)}ms")
                appendLine("- **Maximum target analysis time:** ${maxTime}ms")
                appendLine("- **Minimum target analysis time:** ${minTime}ms")
                appendLine()
            }
        })

        echo("📄 Detailed summary saved to: ${reportFile.absolute()}")
    }

    private fun generateJsonReport(
        output: Path,
        results: ReachabilityResults,
        duration: Double,
    ) {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        // Create individual analysis results
        val analysisResults = results.reachabilityResults.map { result ->
            AnalysisResultDto(
                targetId = generateTargetId(result.target),
                status = result.status.toDto(),
                executionTime = result.executionTimeMs,
                errorMessage = result.errorMessage
            )
        }

        // Calculate summary statistics
        val statusCounts = results.reachabilityResults.groupingBy { it.status }.eachCount()

        val summary = AnalysisSummaryDto(
            totalTargets = results.reachabilityResults.size,
            reachableTargets = statusCounts[ReachabilityStatus.REACHABLE] ?: 0,
            unreachableTargets = statusCounts[ReachabilityStatus.UNREACHABLE] ?: 0,
            unknownTargets = statusCounts[ReachabilityStatus.UNKNOWN] ?: 0
        )

        // Determine project path
        val projectPathStr = when {
            projectPath != null -> projectPath.toString()
            projectIrPaths.isNotEmpty() -> projectIrPaths.first().toString()
            else -> "unknown"
        }

        val jsonOutput = AnalysisReportDto(
            projectPath = projectPathStr,
            solverType = solverType.name,
            totalTime = (duration * 1000.0).toLong(),
            results = analysisResults,
            summary = summary
        )

        val reportFile = output / "reachability_results.json"
        reportFile.writeText(json.encodeToString(jsonOutput))

        echo("📄 JSON report saved to: ${reportFile.absolute()}")
    }

    private fun generateTargetId(target: TsReachabilityTarget): String {
        val method = target.location.location.method
        val className = method.enclosingClass?.name ?: "UnknownClass"
        val methodName = method.name
        val fileName = method.enclosingClass?.signature?.file?.fileName ?: "UnknownFile"

        // val targetType = when (target) {
        //     is TsReachabilityTarget.InitialPoint -> "initial"
        //     is TsReachabilityTarget.IntermediatePoint -> "intermediate"
        //     is TsReachabilityTarget.FinalPoint -> "final"
        // }

        val location = target.location.location
        val locationInfo = when {
            location.blockDtoIndex != null && location.stmtDtoIndex != null -> {
                "b${location.blockDtoIndex}_s${location.stmtDtoIndex}"
            }

            else -> {
                "unknown_loc"
            }
        }

        // Format: ClassName.methodName:location@fileName
        return "${className}.${methodName}:${locationInfo}@${fileName}"
    }

    private fun printSummaryToConsole(results: ReachabilityResults, duration: Double) {
        val statusCounts = results.reachabilityResults.groupingBy { it.status }.eachCount()

        echo("\n📊 ANALYSIS COMPLETE")
        echo("=".repeat(30))
        echo("⏱️ Duration: ${String.format("%.2f", duration)}s")
        echo("🔍 Methods: ${results.methods.size}")
        echo("📍 Targets: ${results.reachabilityResults.size}")
        echo("✅ Reachable: ${statusCounts[ReachabilityStatus.REACHABLE] ?: 0}")
        echo("❌ Unreachable: ${statusCounts[ReachabilityStatus.UNREACHABLE] ?: 0}")
        echo("❓ Unknown: ${statusCounts[ReachabilityStatus.UNKNOWN] ?: 0}")
        if (output != null) {
            echo("📁 Reports saved to: ${output!!.absolute()}")
        } else {
            echo("📁 No output directory specified, reports not saved")
        }
    }
}

enum class AnalysisMode {
    ALL_METHODS,
    PUBLIC_METHODS,
    ENTRY_POINTS,
}

enum class ReachabilityStatus {
    REACHABLE,     // Confirmed reachable with execution path
    UNREACHABLE,   // Confirmed unreachable
    UNKNOWN,       // Could not determine (timeout/approximation/error)
}

fun ReachabilityStatus.toDto(): ReachabilityStatusDto = when (this) {
    ReachabilityStatus.REACHABLE -> ReachabilityStatusDto.REACHABLE
    ReachabilityStatus.UNREACHABLE -> ReachabilityStatusDto.UNREACHABLE
    ReachabilityStatus.UNKNOWN -> ReachabilityStatusDto.UNKNOWN
}

data class ExecutionPath(
    val statements: List<EtsStmt>,
)

data class TargetReachabilityResult(
    val target: TsReachabilityTarget,
    val status: ReachabilityStatus,
    val executionPaths: List<ExecutionPath>,
    val executionTimeMs: Long = 0L,
    val errorMessage: String? = null,
)

data class ReachabilityResults(
    val methods: List<EtsMethod>,
    val targets: List<TsReachabilityTarget>,
    val states: List<TsState>,
    val reachabilityResults: List<TargetReachabilityResult>,
    val scene: EtsScene,
)

/**
 * Represents a trace - an independent hierarchical structure of targets.
 */
sealed interface TargetTrace {
    data class Linear(val targets: List<TargetDto>) : TargetTrace
    data class Tree(val root: TargetTreeNodeDto) : TargetTrace
}

fun main(args: Array<String>) {
    ReachabilityAnalyzer().main(args)
}
