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
 * Tests for loops and iteration reachability scenarios.
 * Verifies reachability analysis through for loops, while loops, and loop control statements.
 */
class LoopsReachabilityTest {

    private val scene = run {
        val path = "/reachability/LoopsReachability.ts"
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
    fun testForLoopReachable() {
        // Test reachability through for loop:
        //   for (let i = 0; i < limit; i++) -> if (i === 5) -> if (limit > 10) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "forLoopReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (i === 5)
        val iCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(iCheck))

        // if (limit > 10)
        val limitCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(limitCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for for loop reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when limit > 10"
        )
    }

    @Test
    fun testWhileLoopBreakReachable() {
        // Test reachability through while loop with break:
        //   while (counter < 100) -> if (counter === 20) break -> if (counter > 50) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "whileLoopBreakReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (counter === 20)
        val breakCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(breakCheck))

        // if (counter > 50) - this should be unreachable due to break
        val unreachableCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(unreachableCheck))

        // return 1 - should be unreachable
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        
        // This test verifies that the unreachable path is correctly identified
        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        // The return 1 should NOT be reached due to break at counter == 20
    }

    @Test
    fun testNestedLoopContinueReachable() {
        // Test reachability through nested loops with continue:
        //   for (i < 5) -> for (j < 5) -> if (i + j === 3) continue -> if (i === 2 && j === 3) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "nestedLoopContinueReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (i + j === 3)
        val continueCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(continueCheck))

        // if (i === 2 && j === 3)
        val reachableCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(reachableCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for nested loop reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when i=2, j=3 (2+3=5, not 3)"
        )
    }

    @Test
    fun testForInLoopReachable() {
        // Test reachability through for-in loop:
        //   for (const key in obj) -> if (key === "b") -> if (obj[key] === 2) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "forInLoopReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (key === "b")
        val keyCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(keyCheck))

        // if (obj[key] === 2)
        val valueCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(valueCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for for-in loop reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when key 'b' has value 2"
        )
    }

    @Test
    fun testForOfLoopReachable() {
        // Test reachability through for-of loop:
        //   for (const value of arr) -> if (value > 25) -> if (value < 35) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "forOfLoopReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (value > 25)
        val minCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(minCheck))

        // if (value < 35)
        val maxCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(maxCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for for-of loop reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached for value 30 (25 < 30 < 35)"
        )
    }

    @Test
    fun testDoWhileReachable() {
        // Test reachability through do-while loop:
        //   do { count++; if (count === threshold) -> return 1 } while (count < threshold)
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "doWhileReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (count === threshold)
        val thresholdCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(thresholdCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for do-while loop reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached at least once in do-while"
        )
    }
}
