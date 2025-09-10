package org.usvm.reachability

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
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
 * Tests for recursion reachability scenarios.
 * Verifies reachability analysis through recursive function calls with controlled depth limits.
 */
class RecursionReachabilityTest {

    private val scene = run {
        val path = "/reachability/RecursionReachability.ts"
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
    fun testFactorialReachable() {
        // Test reachability through factorial wrapper with depth limits:
        //   const result = this.factorial(input) -> if (result === 24) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "factorialReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (result === 24)
        val resultCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(resultCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for factorial reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when input = 4 (4! = 24)"
        )
    }

    @Test
    fun testFactorialDirectReachable() {
        // Test reachability within factorial method itself with depth limits:
        //   if (n <= 1) -> return 1 (base case)
        //   if (n > 5) -> return -1 (depth limit)
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "factorial" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (n <= 1)
        val baseCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(baseCheck))

        // if (n > 5)
        val depthCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(depthCheck))

        // Multiple return paths should be reachable
        val returnStmts = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()
        returnStmts.forEach { returnStmt ->
            target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))
        }

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for direct factorial reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmts.any { it in reachedStatements },
            "Expected at least one return statement to be reached in factorial"
        )
    }

    @Test
    fun testCountdownReachable() {
        // Test reachability through tail recursion with depth limits:
        //   this.countdown(n - 1, accumulator + n) -> if (n === 0 && accumulator > 10) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "countdown" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (n === 0)
        val baseCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(baseCheck))

        // if (accumulator > 10)
        val accumulatorCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(accumulatorCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for tail recursion reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when initial n leads to accumulator > 10"
        )
    }

    @Test
    fun testMutualRecursionReachable() {
        // Test reachability through mutual recursion with depth limits:
        //   if (input > 0 && input < 5) -> const evenResult = this.isEven(input) -> if (evenResult && input === 4) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "mutualRecursionReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (input > 0 && input < 5)
        val rangeCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(rangeCheck))

        // if (evenResult && input === 4)
        val evenAndFourCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(evenAndFourCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for mutual recursion reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached for even number 4"
        )
    }

    @Test
    fun testFibonacciIterativeReachable() {
        // Test reachability through iterative fibonacci approach:
        //   if (n <= 1) return n -> for loop calculation -> if (b === 13) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "fibonacciIterative" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (n <= 1)
        val baseCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(baseCheck))

        // if (n > 10)
        val limitCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(limitCheck))

        // if (b === 13)
        val specialCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[2]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(specialCheck))

        // return 1
        val specialReturn = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[1] // Second return statement
        target.addChild(TsReachabilityTarget.FinalPoint(specialReturn))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for fibonacci iterative reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            specialReturn in reachedStatements,
            "Expected special return statement to be reached when fib(7) = 13"
        )
    }

    @Test
    fun testTreeTraversalReachable() {
        // Test reachability through simplified tree traversal with parameterized target:
        //   if (treeNode.value === target) -> return 1 (reachable when target = 10)
        //   if (treeNode.left.value === target) -> return 2 (reachable when target = 5)
        //   if (treeNode.right.value === target) -> return 3 (reachable when target = 15)
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "treeTraversalReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())

        // Path 1:
        //   if (treeNode.value === target)
        //     return 1
        val ifStmt1 = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        val returnStmt1 = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]

        initialTarget
            .addChild(TsReachabilityTarget.IntermediatePoint(ifStmt1))
            .addChild(TsReachabilityTarget.FinalPoint(returnStmt1))

        // Path 2:
        //   if (treeNode.left.value === target)
        //     return 2
        val ifStmt2 = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[1]
        val returnStmt2 = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[1]
        initialTarget
            .addChild(TsReachabilityTarget.IntermediatePoint(ifStmt2))
            .addChild(TsReachabilityTarget.FinalPoint(returnStmt2))

        // Path 3:
        //   if (treeNode.right.value === target)
        //     return 3
        val ifStmt3 = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[2]
        val returnStmt3 = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[2]
        initialTarget
            .addChild(TsReachabilityTarget.IntermediatePoint(ifStmt3))
            .addChild(TsReachabilityTarget.FinalPoint(returnStmt3))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for tree traversal reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt1 in reachedStatements,
            "Expected 'return 1' to be reached when target = 10"
        )
        assertTrue(
            returnStmt2 in reachedStatements,
            "Expected 'return 2' to be reached when target = 5"
        )
        assertTrue(
            returnStmt3 in reachedStatements,
            "Expected 'return 3' to be reached when target = 15"
        )
    }

    @Test
    fun testSumRecursionReachable() {
        // Test reachability through simple recursive sum with depth limits:
        //   const result = this.sumToN(input) -> if (result === 15) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "sumRecursionReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (result === 15)
        val resultCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(resultCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for sum recursion reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when input = 5 (sum(5) = 15)"
        )
    }

    @Test
    fun testBinarySearchReachable() {
        // Test reachability through binary search with depth limits:
        //   const found = this.binarySearchSimple(sortedArray, 7) -> if (found) -> return 1
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "binarySearchReachable" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // if (found)
        val foundCheck = method.cfg.stmts.filterIsInstance<EtsIfStmt>()[0]
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(foundCheck))

        // return 1
        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for binary search reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmt in reachedStatements,
            "Expected return statement to be reached when target 7 is found"
        )
    }

    @Disabled("Infinite")
    @Test
    fun testIsEvenDirectReachable() {
        // Test reachability within isEven method for mutual recursion:
        //   if (n === 0) return true -> if (n === 1) return false -> if (n > 4) return false -> this.isOdd(n - 1)
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "isEven" }

        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        // Multiple conditional checks in isEven
        val ifStmts = method.cfg.stmts.filterIsInstance<EtsIfStmt>()
        ifStmts.forEach { ifStmt ->
            target = target.addChild(TsReachabilityTarget.IntermediatePoint(ifStmt))
        }

        // Multiple return paths
        val returnStmts = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()
        returnStmts.forEach { returnStmt ->
            target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))
        }

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        assertTrue(
            results.isNotEmpty(),
            "Expected at least one result for isEven direct reachability"
        )

        val reachedStatements = results.flatMap { it.pathNode.allStatements }.toSet()
        assertTrue(
            returnStmts.any { it in reachedStatements },
            "Expected at least one return statement to be reached in isEven"
        )
    }
}
