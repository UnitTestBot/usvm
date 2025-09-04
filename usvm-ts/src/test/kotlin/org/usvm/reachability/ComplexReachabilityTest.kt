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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for complex reachability scenarios combining multiple language constructions.
 * Verifies reachability analysis through combinations of arrays, objects, method calls, and conditional logic.
 */
class ComplexReachabilityTest {

    private val scene = run {
        val path = "/reachability/ComplexReachability.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)
        EtsScene(listOf(file))
    }

    private val options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        exceptionsPropagation = true,
        stopOnTargetsReached = true,
        timeout = 15.seconds,
        stepsFromLastCovered = 3500L,
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
    )

    private val tsOptions = TsOptions()

    @Test
    fun testArrayObjectCombinedReachable() {
        // Test reachability combining array and object operations
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "arrayObjectCombinedReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (objects[0].value < objects[1].value)
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstIf))

        // if (objects[2].value === 30)
        val secondIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(secondIf))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertEquals(
            1,
            results.size,
            "Expected exactly one result for array-object combined reachable path, but got ${results.size}"
        )
    }

    @Test
    fun testMethodArrayManipulationReachable() {
        // Test reachability through method calls with array manipulation
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "methodArrayManipulationReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (processedArr.length > 0)
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstIf))

        // if (processedArr[0] > input)
        val secondIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(secondIf))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertEquals(
            1,
            results.size,
            "Expected exactly one result for method array manipulation reachable path, but got ${results.size}"
        )
    }

    @Test
    fun testObjectMethodCallReachable() {
        // Test reachability through object method calls affecting state
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "objectMethodCallReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (doubled === 30)
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstIf))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertEquals(
            1,
            results.size,
            "Expected exactly one result for object method call reachable path, but got ${results.size}"
        )
    }

    @Test
    fun testConditionalObjectReachable() {
        // Test reachability with conditional object creation and polymorphic method calls
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "conditionalObjectReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (createExpensive)
        val ifStatement = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(ifStatement))

        // if (createExpensive && result > 200)
        val secondIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(secondIf))

        // return 1
        val return1 = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(return1))

        // return 2
        val return2 = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[1]
        target.addChild(TsReachabilityTarget.FinalPoint(return2))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for conditional object reachable path, but got ${results.size}"
        )
        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            return1 in reachedStatements,
            "Expected 'return 1' statement to be reached in conditional object reachable path"
        )
    }

    @Test
    fun testCrossReferenceReachable() {
        // Test reachability with cross-referenced objects forming a graph
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "crossReferenceReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (nodeA.connections.length === 1)
        val lengthCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(lengthCheck))

        // if (nodeA.connections[0].value === 2)
        val connectionValueCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(connectionValueCheck))

        // if (nodeA.connections[0].connections[0].value === 3)
        val chainCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[2]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(chainCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertEquals(
            1,
            results.size,
            "Expected exactly one result for cross-reference reachable path, but got ${results.size}"
        )
    }
}
