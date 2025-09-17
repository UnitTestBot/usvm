package org.usvm.reachability

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
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
 * Tests for field access reachability scenarios.
 * Verifies reachability analysis through object field comparisons and modifications.
 */
class FieldAccessReachabilityTest {

    private val scene = run {
        val path = "/reachability/FieldAccessReachability.ts"
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
    fun testSimpleFieldReachable() {
        // Test reachability through field access:
        //   if (this.x > 0) -> if (this.y < 10) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "simpleFieldReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (this.x > 0)
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstIf))

        // if (this.y < 10)
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

    @Test
    fun testFieldModificationReachable() {
        // Test reachability after field modification:
        //   this.x = value -> if (this.x > 15) -> if (this.x < 25) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "fieldModificationReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (this.x > 15)
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstIf))

        // if (this.x < 25)
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

    @Test
    fun testFieldConstraintUnreachable() {
        // Test unreachability due to field constraints:
        //   this.x = 10 -> if (this.x > 20) -> return -1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "fieldConstraintUnreachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (this.x > 20)
        val ifStmt = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(ifStmt))

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
    fun testObjectCreationReachable() {
        // Test reachability through object creation and field access
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "objectCreationReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (obj.value === 42)
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(firstIf))

        // if (obj.flag)
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

    @Test
    fun testAmbiguousFieldAccess() {
        // Test reachability with ambiguous field access (multiple classes with same field name)
        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "ambiguousFieldAccess" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (useFirst)
        val useFirstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(useFirstIf))

        // if (obj.commonField > 20)
        val fieldCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(fieldCheck))

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
