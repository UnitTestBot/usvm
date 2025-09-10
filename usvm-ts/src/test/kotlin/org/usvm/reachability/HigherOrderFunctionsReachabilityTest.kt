package org.usvm.reachability

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.api.targets.TsTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Tests for higher-order functions and closures reachability scenarios.
 * Verifies reachability analysis through function composition and array processing patterns.
 */
class HigherOrderFunctionsReachabilityTest {

    private val scene = run {
        val path = "/reachability/HigherOrderFunctionsReachability.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)
        EtsScene(listOf(file))
    }

    private val options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        exceptionsPropagation = true,
        stopOnTargetsReached = true,
        timeout = Duration.INFINITE,
        stepsFromLastCovered = 3500L,
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
    )

    private val tsOptions = TsOptions()

    @Test
    fun testFunctionFactoryReachable() {
        // Test reachability through function factory:
        //   const factor = 3 -> if (factor > 2) -> const result = value * factor -> if (result === 15) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "functionFactoryReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (factor > 2)
        val factorCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(factorCheck))

        // if (result === 15)
        val resultCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(resultCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for function factory reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when value = 5 and factor = 3 (5 * 3 = 15)"
        )
    }

    @Test
    fun testCallbackReachable() {
        // Test reachability through callback-like array processing:
        //   return this.processWithCallback(numbers, multiplier) -> 30 * 5 = 150 > 100 -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "callbackReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // return statement from processWithCallback
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for callback reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when callback produces value > 100"
        )
    }

    @Test
    fun testProcessWithCallbackReachable() {
        // Test the helper method processWithCallback:
        //   for (const item of arr) -> const processed = item * multiplier -> if (processed > 100) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "processWithCallback" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (processed > 100)
        val processedCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(processedCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for processWithCallback reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when any item * multiplier > 100"
        )
    }

    @Test
    fun testClosureReachable() {
        // Test reachability through closure-like counter:
        //   counter += 5 -> counter += 10 -> if (counter === 15) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "closureReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (counter === 15)
        val counterCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(counterCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for closure reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when accumulated counter equals 15"
        )
    }

    @Test
    fun testCompositionReachable() {
        // Test reachability through function composition:
        //   const doubled = input * 2 -> const addedTen = doubled + 10 -> if (addedTen === 30) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "compositionReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (addedTen === 30)
        val resultCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(resultCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for function composition reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when input = 10: double(10) = 20, addTen(20) = 30"
        )
    }

    @Test
    fun testArrowFunctionReachable() {
        // Test reachability through arrow function:
        //   const result = input > 5 ? input * 2 : input -> if (result === 14) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "arrowFunctionReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (result === 14)
        val resultCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(resultCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for arrow function reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached: 7 > 5, so 7 * 2 = 14"
        )
    }

    @Test
    fun testMapOperationReachable() {
        // Test reachability through map-like operation:
        //   for (const value of values) -> const transformed = value * 3 -> if (transformed > 50) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "mapOperationReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (transformed > 50)
        val transformedCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(transformedCheck))

        // if (hasLargeValue)
        val hasLargeValueCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(hasLargeValueCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for map operation reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when any value * 3 > 50"
        )
    }

    @Test
    fun testFilterOperationReachable() {
        // Test reachability through filter-like operation:
        //   for (const value of values) -> if (value > 10) filteredCount++ -> if (filteredCount === 2) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "filterOperationReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (value > 10)
        val valueCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(valueCheck))

        // if (filteredCount === 2)
        val countCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(countCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for filter operation reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when exactly 2 values are > 10"
        )
    }
}
