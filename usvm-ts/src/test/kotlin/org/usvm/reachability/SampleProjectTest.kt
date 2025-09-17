package org.usvm.reachability

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.api.targets.TsTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sample Project Reachability Test
 *
 * This test serves as a manual entry point to run reachability analysis
 * on the sample TypeScript project from IntelliJ IDEA.
 *
 * To run from IDEA:
 * 1. Right-click on this test class
 * 2. Select "Run 'SampleProjectTest'" or use Ctrl+Shift+F10
 * 3. View the analysis results in the console output
 */
class SampleProjectTest {

    companion object {
        private const val DEBUG = false

        // Common default options for all tests
        private val DEFAULT_MACHINE_OPTIONS = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            exceptionsPropagation = true,
            stopOnTargetsReached = false,
            timeout = if (DEBUG) Duration.INFINITE else 30.seconds,
            stepsFromLastCovered = 1000L,
            solverType = SolverType.YICES,
        )

        private val DEFAULT_TS_OPTIONS = TsOptions()
    }

    @Test
    fun `analyze ProcessManager reachability`() {
        println("🚀 Starting TypeScript Reachability Analysis on Sample Project")
        println("=".repeat(60))

        val sampleProjectPath = getSampleProjectPath()
        println("📁 Project path: $sampleProjectPath")

        // Load TypeScript files
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        println("📄 Found ${tsFiles.size} TypeScript files:")
        tsFiles.forEach { println("   - ${it.name}") }

        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })
        println("📊 Loaded scene with ${scene.projectClasses.size} classes")

        // Use default options
        val observer = ReachabilityObserver()
        val machine = TsMachine(scene, DEFAULT_MACHINE_OPTIONS, DEFAULT_TS_OPTIONS, machineObserver = observer)

        // Find all methods in the sample project
        val allMethods = scene.projectClasses.flatMap { it.methods }
        println("🔍 Found ${allMethods.size} methods to analyze:")

        allMethods.forEach { method ->
            println("   - ${method.enclosingClass?.name ?: "Unknown"}.${method.name}")
        }

        // Create interesting reachability targets for the system classes
        val targets = createSampleTargets(scene)
        println(
            "🎯 Created ${targets.size} reachability targets: ${
                targets.count { it.location is EtsThrowStmt }
            } throws, ${
                targets.count { it.location is EtsReturnStmt }
            } returns, ${
                targets.count { it.location is EtsIfStmt }
            } branches"
        )

        // Run the analysis
        println("\n⚡ Running reachability analysis...")
        val results = machine.analyze(allMethods, targets)

        // Process and display results
        displayResults(results, targets)
    }

    @Test
    fun `analyze ProcessManager state transitions`() {
        println("🎯 Focused Analysis: Process State Transitions")
        println("=".repeat(50))

        val sampleProjectPath = getSampleProjectPath()
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })

        // Find Process class and its state transition methods
        val processClass = scene.projectClasses.find { it.name == "Process" }
            ?: error("Process class not found")

        val stateTransitionMethods = processClass.methods.filter { method ->
            method.name in listOf("start", "pause", "block", "unblock", "terminate")
        }

        println("🔍 Analyzing process state transition methods:")
        stateTransitionMethods.forEach { method ->
            println("   - ${processClass.name}.${method.name}")
        }

        val observer = ReachabilityObserver()
        val machine = TsMachine(scene, DEFAULT_MACHINE_OPTIONS, DEFAULT_TS_OPTIONS, machineObserver = observer)

        // Create specific targets for state transitions
        val targets = createStateTransitionTargets(stateTransitionMethods)
        println("📍 Created ${targets.size} specific targets for state transitions")

        val results = machine.analyze(stateTransitionMethods, targets)

        displayDetailedResults(results, targets, stateTransitionMethods.first())
    }

    @Test
    fun `analyze MemoryManager operations`() {
        println("🧮 Analysis: Memory Management Operations")
        println("=".repeat(50))

        val sampleProjectPath = getSampleProjectPath()
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })

        val memoryManagerClass = scene.projectClasses.find { it.name == "MemoryManager" }
            ?: error("MemoryManager class not found")

        val memoryMethods = memoryManagerClass.methods.filter { method ->
            method.name in listOf("allocateMemory", "freeMemory", "compactMemory", "defragmentMemory")
        }

        println("🔍 Found ${memoryMethods.size} memory management methods:")
        memoryMethods.forEach { println("   - ${it.name}") }

        // Override options for memory management analysis
        val machineOptions = DEFAULT_MACHINE_OPTIONS.copy(
            stepsFromLastCovered = 2000L
        )

        val machine = TsMachine(scene, machineOptions, DEFAULT_TS_OPTIONS, machineObserver = ReachabilityObserver())
        val targets = createMemoryManagerTargets(memoryManagerClass)

        val results = machine.analyze(memoryMethods, targets)
        displayResults(results, targets)
    }

    @Test
    fun `demonstrate array operations reachability`() {
        println("🛤️ Demonstration: Array Operations Reachability Analysis")
        println("=".repeat(60))

        val sampleProjectPath = getSampleProjectPath()
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })

        val processClass = scene.projectClasses.find { it.name == "Process" }
            ?: error("Process class not found")

        val arrayMethod = processClass.methods.find { it.name == "addChild" }
            ?: error("Array manipulation method not found")

        println("🔍 Analyzing method: ${processClass.name}.${arrayMethod.name}")

        // Override options for focused array operation analysis
        val machineOptions = DEFAULT_MACHINE_OPTIONS.copy(
            stepsFromLastCovered = 1000L
        )

        val observer = ReachabilityObserver()
        val machine = TsMachine(scene, machineOptions, DEFAULT_TS_OPTIONS, machineObserver = observer)

        // Create targets for different execution paths in array operations
        val targets = createMethodPathTargets(arrayMethod)
        println("📍 Created ${targets.size} path targets")

        val results = machine.analyze(listOf(arrayMethod), targets)

        displayPathExtractionResults(results, targets, arrayMethod)
    }

    private fun getSampleProjectPath(): File {
        // Get the project root directory
        val currentDir = File(System.getProperty("user.dir"))
        val projectRoot = findProjectRoot(currentDir)
        return File(projectRoot, "examples/reachability/sample-project")
    }

    private fun findProjectRoot(dir: File): File {
        if (File(dir, "LICENSE").exists()) {
            return dir
        }
        val parent = dir.parentFile
        if (parent != null && parent != dir) {
            return findProjectRoot(parent)
        }
        error("Could not find project root")
    }

    private fun findTypeScriptFiles(projectDir: File): List<File> {
        return projectDir.walkTopDown()
            .filter { it.isFile && it.extension == "ts" }
            .toList()
    }

    private fun createSampleTargets(scene: EtsScene): List<TsTarget> {
        val targets = mutableListOf<TsTarget>()

        scene.projectClasses.forEach { clazz ->
            clazz.methods.forEach { method ->
                method.cfg.stmts.forEach { stmt ->
                    // Create targets for different types of statements
                    when (stmt) {
                        is EtsThrowStmt -> {
                            targets.add(TsReachabilityTarget.FinalPoint(stmt))
                        }

                        is EtsReturnStmt -> {
                            targets.add(TsReachabilityTarget.FinalPoint(stmt))
                        }

                        is EtsIfStmt -> {
                            targets.add(TsReachabilityTarget.IntermediatePoint(stmt))
                        }
                    }
                }
            }
        }

        return targets
    }

    private fun createStateTransitionTargets(methods: List<org.jacodb.ets.model.EtsMethod>): List<TsTarget> {
        val targets = mutableListOf<TsTarget>()

        methods.forEach { method ->
            method.cfg.stmts.forEach { stmt ->
                // Create targets for state transition statements
                when {
                    stmt.toString().contains("start") -> {
                        targets.add(TsReachabilityTarget.InitialPoint(stmt))
                    }

                    stmt.toString().contains("terminate") -> {
                        targets.add(TsReachabilityTarget.FinalPoint(stmt))
                    }

                    stmt.toString().contains("pause") || stmt.toString().contains("unblock") -> {
                        targets.add(TsReachabilityTarget.IntermediatePoint(stmt))
                    }
                }
            }
        }

        return targets
    }

    private fun createMemoryManagerTargets(clazz: org.jacodb.ets.model.EtsClass): List<TsTarget> {
        val targets = mutableListOf<TsTarget>()

        clazz.methods.forEach { method ->
            method.cfg.stmts.forEach { stmt ->
                if (stmt.toString().contains("return") || stmt.toString().contains("throw")) {
                    targets.add(TsReachabilityTarget.FinalPoint(stmt))
                }
            }
        }

        return targets
    }

    private fun createMethodPathTargets(method: org.jacodb.ets.model.EtsMethod): List<TsTarget> {
        val targets = mutableListOf<TsTarget>()
        val statements = method.cfg.stmts

        if (statements.isNotEmpty()) {
            // Add initial point
            targets.add(TsReachabilityTarget.InitialPoint(statements.first()))

            // Add intermediate points for interesting statements
            statements.drop(1).dropLast(1).forEach { stmt ->
                if (stmt.toString().contains("if") || stmt.toString().contains("throw")) {
                    targets.add(TsReachabilityTarget.IntermediatePoint(stmt))
                }
            }

            // Add final point
            if (statements.size > 1) {
                targets.add(TsReachabilityTarget.FinalPoint(statements.last()))
            }
        }

        return targets
    }

    private fun displayResults(results: List<TsState>, targets: List<TsTarget>) {
        println("\n📊 REACHABILITY ANALYSIS RESULTS")
        println("=".repeat(50))
        println("Total states explored: ${results.size}")
        println("Total targets defined: ${targets.size}")

        val reachable = mutableListOf<TsTarget>()
        val unreachable = mutableListOf<TsTarget>()
        val unknown = mutableListOf<TsTarget>()

        // Get all statements that were explored during analysis
        val allExploredStatements = results.flatMap { state ->
            state.pathNode.allStatements
        }.toSet()

        // Analyze which targets were reached
        targets.forEach { target ->
            val wasReached = results.any { state ->
                state.pathNode.allStatements.contains(target.location)
            }

            when {
                wasReached -> {
                    reachable.add(target)
                }

                // If the statement was explored but never reached as a target,
                // it might be unreachable due to path constraints
                allExploredStatements.contains(target.location) -> {
                    unreachable.add(target)
                }

                // If the statement was never even explored, it's unknown
                else -> {
                    unknown.add(target)
                }
            }
        }

        println("\n✅ REACHABLE TARGETS (${reachable.size}):")
        reachable.forEach { target ->
            println("   ✓ ${target::class.simpleName}: ${target.location}")
        }

        println("\n❌ UNREACHABLE TARGETS (${unreachable.size}):")
        unreachable.forEach { target ->
            println("   ✗ ${target::class.simpleName}: ${target.location}")
        }

        println("\n❓ UNKNOWN/TIMEOUT TARGETS (${unknown.size}):")
        unknown.forEach { target ->
            println("   ? ${target::class.simpleName}: ${target.location}")
        }

        println("\n📈 SUMMARY:")
        println("   Reachable: ${reachable.size}")
        println("   Unreachable: ${unreachable.size}")
        println("   Unknown: ${unknown.size}")
    }

    private fun displayDetailedResults(
        results: List<TsState>,
        targets: List<TsTarget>,
        method: org.jacodb.ets.model.EtsMethod,
    ) {
        println("\n🔍 DETAILED ANALYSIS: ${method.name}")
        println("=".repeat(60))

        displayResults(results, targets)

        println("\n📋 METHOD STRUCTURE:")
        method.cfg.stmts.forEachIndexed { index, stmt ->
            println("   ${index + 1}. ${stmt::class.simpleName}: ${stmt.toString().take(80)}...")
        }

        if (results.isNotEmpty()) {
            println("\n🛤️ EXECUTION PATHS FOUND:")
            results.take(5).forEachIndexed { index, state ->
                println("   Path ${index + 1}: ${state::class.simpleName}")
                println("      Statements covered: ${state.pathNode.allStatements.count()}")
            }
        }
    }

    private fun displayPathExtractionResults(
        results: List<TsState>,
        targets: List<TsTarget>,
        method: org.jacodb.ets.model.EtsMethod,
    ) {
        println("\n🛤️ PATH EXTRACTION DEMONSTRATION")
        println("=".repeat(50))

        println("Method: ${method.name}")
        println("Total execution states: ${results.size}")
        println("Targets analyzed: ${targets.size}")

        if (results.isNotEmpty()) {
            println("\n📍 DETAILED PATH ANALYSIS:")

            results.take(3).forEachIndexed { index, state ->
                println("\n--- Path ${index + 1} ---")
                println("State type: ${state::class.simpleName}")
                println("Statements in path: ${state.pathNode.allStatements.count()}")

                // Show the execution path
                println("Execution sequence:")
                state.pathNode.allStatements.take(10).forEachIndexed { stmtIndex: Int, stmt ->
                    println("  ${stmtIndex + 1}. ${stmt::class.simpleName}: ${stmt.toString().take(60)}...")
                }

                val stmtCount = state.pathNode.allStatements.count()
                if (stmtCount > 10) {
                    println("  ... and ${stmtCount - 10} more statements")
                }

                // Check which targets this path reached
                val reachedTargets = targets.filter { target ->
                    state.pathNode.allStatements.contains(target.location)
                }

                println("Targets reached in this path: ${reachedTargets.size}")
                reachedTargets.forEach { target ->
                    println("  - ${target::class.simpleName}")
                }
            }

            if (results.size > 3) {
                println("\n... and ${results.size - 3} more execution paths")
            }
        }
    }
}
