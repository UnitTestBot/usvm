package org.usvm.reachability

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.reachability.api.TsReachabilityObserver
import org.usvm.reachability.api.TsReachabilityTarget
import org.usvm.api.TsTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Tests for array access reachability scenarios.
 * Verifies reachability analysis through array element comparisons and operations.
 */
class ArrayReachabilityTest {

    private val scene = run {
        val path = "/reachability/ArrayReachability.ts"
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
    fun testSimpleArrayReachable() {
        // Test reachability through array access: arr[0] === 10 -> arr[1] > 15 -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "simpleArrayReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (arr[0] === 10)
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstIf))

        // if (arr[1] > 15)
        val secondIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(secondIf))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result",
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached in execution path"
        )
    }

    @Disabled("Extra exceptional path is found")
    @Test
    fun testArrayModificationReachable() {
        // Test reachability after array modification: arr[index] = value -> arr[index] > 10 -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "arrayModificationReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (index >= 0 && index < 3)
        val boundsCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(boundsCheck))

        // if (arr[index] > 10)
        val check = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(check))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result",
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached in execution path"
        )
    }

    @Test
    fun testArrayBoundsUnreachable() {
        // Test unreachability due to array element constraints: arr[0] > 20 (when arr[0] = 5)
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "arrayBoundsUnreachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (arr[0] > 20)
        val check = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(check))

        // return -1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))

        // In targeted mode, results may be non-empty as machine explores paths toward targets
        // But the unreachable return statement should not be reached in any execution path
        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt !in reachedStatements,
            "Unreachable return statement was reached in execution path"
        )
    }

    @Test
    fun testArraySumReachable() {
        // Test reachability through array sum calculation: sum === 30 -> arr[0] < arr[1] -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "arraySumReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (sum === 30)
        val sumCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(sumCheck))

        // if (arr[0] < arr[1])
        val comparison = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(comparison))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result",
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached in execution path"
        )
    }

    @Disabled("Nested array access becomes field access")
    @Test
    fun testNestedArrayReachable() {
        // Test reachability through nested array access: matrix[0][0] === 1 -> matrix[1][1] === 4 -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "nestedArrayReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (matrix[0][0] === 1)
        val firstCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstCheck))

        // if (matrix[1][1] === 4)
        val secondCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(secondCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result",
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached in execution path"
        )
    }
}
