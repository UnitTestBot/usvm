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
 * Tests for type guards and assertions reachability scenarios.
 * Verifies reachability analysis through typeof checks, instanceof operations, and user-defined type guards.
 */
class TypeGuardsReachabilityTest {

    private val scene = run {
        val path = "/reachability/TypeGuardsReachability.ts"
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
    fun testTypeofGuardReachable() {
        // Test reachability through typeof type guard with constant string comparison:
        //   if (typeof value === "string") -> if (value === "hello") -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "typeofGuardReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (typeof value === "string")
        val typeofCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(typeofCheck))

        // if (value === "hello")
        val stringCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(stringCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for typeof guard reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when value is exactly 'hello'"
        )
    }

    @Test
    fun testInstanceofGuardReachable() {
        // Test reachability through instanceof type guard:
        //   if (obj instanceof Array) -> if (obj.length === 3) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "instanceofGuardReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (obj instanceof Array)
        val instanceofCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(instanceofCheck))

        // if (obj.length === 3)
        val lengthCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(lengthCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for instanceof guard reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when obj is array with length 3"
        )
    }

    @Test
    fun testUserDefinedGuardReachable() {
        // Test reachability through user-defined type guard:
        //   if (this.isString(input)) -> if (input === "test") -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "userDefinedGuardReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (this.isString(input))
        val guardCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(guardCheck))

        // if (input === "test")
        val testCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(testCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for user-defined guard reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when input is exactly 'test'"
        )
    }

    @Test
    fun testTypeAssertionReachable() {
        // Test reachability through type assertion:
        //   const str = value as string -> if (str === "A") -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "typeAssertionReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (str === "A")
        val assertionCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(assertionCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for type assertion reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when value is exactly 'A'"
        )
    }

    @Test
    fun testNonNullAssertionReachable() {
        // Test reachability through non-null assertion:
        //   if (value !== null) -> const definiteString = value! -> if (definiteString === "nonempty") -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "nonNullAssertionReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (value !== null)
        val nullCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(nullCheck))

        // if (definiteString === "nonempty")
        val stringCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(stringCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for non-null assertion reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when value is non-null 'nonempty'"
        )
    }

    @Test
    fun testNumericTypeGuardReachable() {
        // Test reachability through numeric type guard:
        //   if (typeof value === "number") -> if (value > 10) -> if (value < 20) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "numericTypeGuardReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (typeof value === "number")
        val typeofCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(typeofCheck))

        // if (value > 10)
        val minCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(minCheck))

        // if (value < 20)
        val maxCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[2]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(maxCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for numeric type guard reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached for numbers between 10 and 20"
        )
    }

    @Test
    fun testBooleanTypeGuardReachable() {
        // Test reachability through boolean type guard:
        //   if (typeof value === "boolean") -> if (value === true) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "booleanTypeGuardReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (typeof value === "boolean")
        val typeofCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(typeofCheck))

        // if (value === true)
        val boolCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(boolCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for boolean type guard reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when value is boolean true"
        )
    }

    @Test
    fun testObjectTypeGuardReachable() {
        // Test reachability through object type guard:
        //   if (typeof value === "object" && value !== null) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "objectTypeGuardReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (typeof value === "object" && value !== null)
        val objectCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(objectCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for object type guard reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached for non-null objects"
        )
    }

    @Test
    fun testComplexTypeGuardReachable() {
        // Test reachability through complex type guard combination:
        //   if (typeof value === "object" && value !== null) -> if (value instanceof Date) -> if (value.getFullYear() > 2020) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "complexTypeGuardReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (typeof value === "object" && value !== null)
        val objectCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(objectCheck))

        // if (value instanceof Date)
        val dateCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(dateCheck))

        // if (value.getFullYear() > 2020)
        val yearCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[2]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(yearCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for complex type guard reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached for dates after 2020"
        )
    }
}
