package org.usvm.reachability

import org.jacodb.ets.model.EtsScene
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

    @Test
    fun `analyze Calculator class reachability`() {
        println("🚀 Starting TypeScript Reachability Analysis on Sample Project")
        println("=" + "=".repeat(59))

        val sampleProjectPath = getSampleProjectPath()
        println("📁 Project path: $sampleProjectPath")

        // Load TypeScript files
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        println("📄 Found ${tsFiles.size} TypeScript files:")
        tsFiles.forEach { println("   - ${it.name}") }

        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })
        println("📊 Loaded scene with ${scene.projectClasses.size} classes")

        // Configure analysis options
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            exceptionsPropagation = true,
            stopOnTargetsReached = false, // Continue to find all paths
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3500L,
            solverType = SolverType.YICES,
        )

        val tsOptions = TsOptions()
        val observer = ReachabilityObserver()
        val machine = TsMachine(scene, machineOptions, tsOptions, machineObserver = observer)

        // Find all methods in the sample project
        val allMethods = scene.projectClasses.flatMap { it.methods }
        println("🔍 Found ${allMethods.size} methods to analyze:")

        allMethods.forEach { method ->
            println("   - ${method.enclosingClass?.name ?: "Unknown"}.${method.name}")
        }

        // Create interesting reachability targets for the Calculator class
        val targets = createSampleTargets(scene)
        println("🎯 Created ${targets.size} reachability targets")

        // Run the analysis
        println("\n⚡ Running reachability analysis...")
        val results = machine.analyze(allMethods, targets)

        // Process and display results
        displayResults(results, targets)
    }

    @Test
    fun `analyze specific Calculator methods`() {
        println("🎯 Focused Analysis: Calculator Complex Operations")
        println("=" + "=".repeat(49))

        val sampleProjectPath = getSampleProjectPath()
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })

        // Find Calculator class and its complexOperation method
        val calculatorClass = scene.projectClasses.find { it.name.contains("Calculator") }
            ?: throw IllegalStateException("Calculator class not found")

        val complexOperationMethod = calculatorClass.methods
            .find { it.name == "complexOperation" }
            ?: throw IllegalStateException("complexOperation method not found")

        println("🔍 Analyzing: ${calculatorClass.name}.${complexOperationMethod.name}")

        // Configure for detailed analysis
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            exceptionsPropagation = true,
            stopOnTargetsReached = false,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 5000L,
            solverType = SolverType.YICES,
        )

        val observer = ReachabilityObserver()
        val machine = TsMachine(scene, machineOptions, TsOptions(), machineObserver = observer)

        // Create specific targets for different branches in complexOperation
        val targets = createComplexOperationTargets(complexOperationMethod)
        println("📍 Created ${targets.size} specific targets for complex operation branches")

        val results = machine.analyze(listOf(complexOperationMethod), targets)

        displayDetailedResults(results, targets, complexOperationMethod)
    }

    @Test
    fun `analyze MathUtils static methods`() {
        println("🧮 Analysis: MathUtils Static Methods")
        println("=" + "=".repeat(44))

        val sampleProjectPath = getSampleProjectPath()
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })

        val mathUtilsClass = scene.projectClasses.find { it.name.contains("MathUtils") }
            ?: throw IllegalStateException("MathUtils class not found")

        val staticMethods = mathUtilsClass.methods.filter { it.isStatic }
        println("🔍 Found ${staticMethods.size} static methods:")
        staticMethods.forEach { println("   - ${it.name}") }

        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 2000L,
            solverType = SolverType.Z3,
        )

        val machine = TsMachine(scene, machineOptions, TsOptions(), machineObserver = ReachabilityObserver())
        val targets = createMathUtilsTargets(mathUtilsClass).filter { it.location!!.location.method.name == "gcd" }

        val results = machine.analyze(staticMethods, targets)
        displayResults(results, targets)
    }

    @Test
    fun `demonstrate path extraction from reachability results`() {
        println("🛤️ Demonstration: Path Extraction from Reachability Analysis")
        println("=" + "=".repeat(58))

        val sampleProjectPath = getSampleProjectPath()
        val tsFiles = findTypeScriptFiles(sampleProjectPath)
        val scene = EtsScene(tsFiles.map { loadEtsFileAutoConvert(it.toPath()) })

        val calculatorClass = scene.projectClasses.find { it.name.contains("Calculator") }
            ?: throw IllegalStateException("Calculator class not found")

        val addMethod = calculatorClass.methods.find { it.name == "add" }
            ?: throw IllegalStateException("add method not found")

        println("🔍 Analyzing method: ${calculatorClass.name}.${addMethod.name}")

        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            exceptionsPropagation = true,
            stopOnTargetsReached = false,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 1000L,
            solverType = SolverType.YICES,
        )

        val observer = ReachabilityObserver()
        val machine = TsMachine(scene, machineOptions, TsOptions(), machineObserver = observer)

        // Create targets for different execution paths in the add method
        val targets = createMethodPathTargets(addMethod)
        println("📍 Created ${targets.size} path targets")

        val results = machine.analyze(listOf(addMethod), targets)

        displayPathExtractionResults(results, targets, addMethod)
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
        throw IllegalStateException("Could not find project root")
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
                    when {
                        stmt.toString().contains("throw") -> {
                            targets.add(TsReachabilityTarget.FinalPoint(stmt))
                        }
                        stmt.toString().contains("return") -> {
                            targets.add(TsReachabilityTarget.FinalPoint(stmt))
                        }
                        stmt.toString().contains("if") -> {
                            targets.add(TsReachabilityTarget.IntermediatePoint(stmt))
                        }
                    }
                }
            }
        }

        return targets
    }

    private fun createComplexOperationTargets(method: org.jacodb.ets.model.EtsMethod): List<TsTarget> {
        val targets = mutableListOf<TsTarget>()
        val statements = method.cfg.stmts

        statements.forEachIndexed { index, stmt ->
            when (index) {
                0 -> targets.add(TsReachabilityTarget.InitialPoint(stmt))
                statements.size - 1 -> targets.add(TsReachabilityTarget.FinalPoint(stmt))
                else -> targets.add(TsReachabilityTarget.IntermediatePoint(stmt))
            }
        }

        return targets
    }

    private fun createMathUtilsTargets(clazz: org.jacodb.ets.model.EtsClass): List<TsTarget> {
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
        println("=" + "=".repeat(49))
        println("Total states explored: ${results.size}")
        println("Total targets defined: ${targets.size}")

        val reachable = mutableListOf<TsTarget>()
        val unreachable = mutableListOf<TsTarget>()
        val unknown = mutableListOf<TsTarget>()

        // Analyze which targets were reached
        targets.forEach { target ->
            val wasReached = results.any { state ->
                state.pathNode.allStatements.contains(target.location)
            }

            when {
                wasReached -> reachable.add(target)
                else -> unknown.add(target) // For this demo, we'll mark others as unknown
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
        method: org.jacodb.ets.model.EtsMethod
    ) {
        println("\n🔍 DETAILED ANALYSIS: ${method.name}")
        println("=" + "=".repeat(59))

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
        method: org.jacodb.ets.model.EtsMethod
    ) {
        println("\n🛤️ PATH EXTRACTION DEMONSTRATION")
        println("=" + "=".repeat(49))

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
