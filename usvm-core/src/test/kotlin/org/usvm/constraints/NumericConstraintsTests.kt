package org.usvm.constraints

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.utils.getValue
import io.ksmt.utils.powerOfTwo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UNotExpr
import org.usvm.USizeSort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isFalse
import org.usvm.logger
import org.usvm.regions.IntIntervalsRegion
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumericConstraintsTests {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var ownership: MutabilityOwnership
    private lateinit var bvSort: UBvSort
    private lateinit var constraints: UNumericConstraints<UBvSort>
    private var previousConstraints: UNumericConstraints<UBvSort>? = null
    private lateinit var unsimplifiedConstraints: MutableList<UBoolExpr>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        ownership = MutabilityOwnership()
        bvSort = ctx.mkBvSort(sizeBits = 8u)

        resetConstraints()
    }

    private fun resetConstraints() {
        constraints = UNumericConstraints(ctx, bvSort, ownership)
        previousConstraints = null

        unsimplifiedConstraints = mutableListOf()
    }

    @Test
    fun testRandom1(): Unit = repeatWithRandom(RANDOM_TEST_1_ITERATIONS) { testRandomConstraints(seed = it, size = 1) }

    @Test
    fun testRandom2(): Unit = repeatWithRandom(RANDOM_TEST_2_ITERATIONS) { testRandomConstraints(seed = it, size = 2) }

    @Test
    fun testRandom3(): Unit = repeatWithRandom(RANDOM_TEST_3_ITERATIONS) { testRandomConstraints(seed = it, size = 3) }

    @Test
    fun testRandom4(): Unit = repeatWithRandom(RANDOM_TEST_4_ITERATIONS) { testRandomConstraints(seed = it, size = 4) }

    @Test
    fun testLinearPattern(): Unit = KZ3Solver(ctx).use { solver ->
        val bound = ctx.mkConst("bound", bvSort)
        var x: UExpr<UBvSort> = ctx.mkConst("x", bvSort)

        addConstraint(ctx.mkBvSignedLessExprNoSimplify(bound, ctx.mkBv(5, bvSort)))

        for (i in 0 until 20) {
            val boundCheck = ctx.mkBvSignedLessExprNoSimplify(x, bound)

            addConstraint(boundCheck)
            solver.checkConstraints(i)

            x = ctx.mkBvAddExpr(x, ctx.mkBv(1, bvSort))
        }
    }

    @Test
    fun testLinearPatternConstraintPropagation(): Unit = KZ3Solver(ctx).use { solver ->
        bvSort = ctx.mkBvSort(sizeBits = 32u)
        constraints = UNumericConstraints(ctx, bvSort, ownership)

        val bound = ctx.mkConst("bound", bvSort)
        var x: UExpr<UBvSort> = ctx.mkConst("x", bvSort)

        for (i in 0 until 20) {
            val boundCheck = ctx.mkBvSignedLessExprNoSimplify(x, bound)

            addConstraint(boundCheck)
            solver.checkConstraints(i)

            x = ctx.mkBvAddExpr(x, ctx.mkBv(1, bvSort))
        }

        addConstraint(ctx.mkBvSignedLessExprNoSimplify(bound, ctx.mkBv(100, bvSort)))
        solver.checkConstraints(0)
    }

    @Test
    fun testConcreteBoundsSimplification(): Unit = with(ctx) {
        KZ3Solver(ctx).use { solver ->
            bvSort = ctx.mkBvSort(sizeBits = 4u)
            constraints = UNumericConstraints(ctx, bvSort, ownership)
            val x by bvSort

            val zero = mkBv(0, bvSort)
            val four = mkBv(4, bvSort)
            val xMinusOne = mkBvAddExpr(x, mkBv(-1, bvSort))

            addConstraint(mkBvSignedLessOrEqualExpr(zero, x))
            addConstraint(mkBvSignedLessOrEqualExpr(x, four))
            addConstraint(mkBvSignedLessOrEqualExpr(zero, xMinusOne))

            solver.checkConstraints(0)
        }
    }

    @Test
    fun test() = with(ctx) {
        val a = ctx.mkConst("a", bvSort)
        val b = ctx.mkConst("b", bvSort)
        val c = ctx.mkConst("c", bvSort)

        val constraintsArray = arrayOf(
            mkNotNoSimplify(mkBvSignedLessOrEqualExprNoSimplify(
                mkBvAddExprNoSimplify(mkBvNegationExprNoSimplify(c), mkBvNegationExprNoSimplify(b)),
                mkBvAddExprNoSimplify(c, mkBv(0xEE, bvSort)))),
            mkNotNoSimplify(mkBvSignedGreaterOrEqualExprNoSimplify(mkBvAddExprNoSimplify(b, a), mkBv(0x3E, bvSort))),
            mkNotNoSimplify(mkBvSignedGreaterOrEqualExprNoSimplify(
                mkBvAddExprNoSimplify(c, mkBv(0xF8, bvSort)),
                mkBvAddExprNoSimplify(mkBvNegationExprNoSimplify(b), mkBvNegationExprNoSimplify(a)))),
            mkBvSignedGreaterOrEqualExprNoSimplify(mkBv(0x79, bvSort),
                mkBvAddExprNoSimplify(mkBvNegationExprNoSimplify(b), mkBv(0x48, bvSort))),
            mkEqNoSimplify(mkBv(0x05, bvSort), mkBvAddExprNoSimplify(
                mkBvNegationExprNoSimplify(b), mkBvNegationExprNoSimplify(a))),
            mkNotNoSimplify(mkBvSignedGreaterOrEqualExprNoSimplify(
                mkBvAddExprNoSimplify(mkBv(0xAD, bvSort), a), mkBv(0x65, bvSort))),
            mkNotNoSimplify(mkBvSignedLessOrEqualExprNoSimplify(
                mkBvAddExprNoSimplify(mkBv(0x6E, bvSort), mkBvNegationExprNoSimplify(c)),
                mkBvAddExprNoSimplify(mkBv(0xE0, bvSort), mkBvNegationExprNoSimplify(b)))
            )
        )

        KYicesSolver(ctx).use { solver ->
            for (constraint in constraintsArray) {
                addConstraint(constraint)
                solver.checkConstraints(0)
            }
        }
    }

    @Test
    fun testEvalInterval(): Unit = with(ctx) {
        bvSort = ctx.mkBvSort(sizeBits = 32u)
        constraints = UNumericConstraints(ctx, bvSort, ownership)
        val x by bvSort

        // x in [-5, -1] U [1, 5]
        constraints.addNumericConstraint(mkBvSignedGreaterOrEqualExpr(x, mkBv(-5, bvSort)))
        constraints.addNumericConstraint(mkBvSignedLessOrEqualExpr(x, mkBv(5, bvSort)))
        constraints.addNegatedNumericConstraint(mkEq(x, mkBv(0, bvSort)))

        val expr = mkBvAddExpr(x, mkBv(3, bvSort))

        // expr in [-2, 2] U [4, 8]
        val expectedInterval = IntIntervalsRegion.ofClosed(
            -2,
            8
        ).subtract(
            IntIntervalsRegion.point(3)
        )

        val actualInterval = constraints.evalInterval(expr)
        assertEquals(expectedInterval, actualInterval)
    }

    private fun testRandomConstraints(seed: Int, size: Int) = KYicesSolver(ctx).use { solver ->
        testRandomConstraints(solver, seed, size)
    }

    private fun testRandomConstraints(solver: KSolver<*>, seed: Int, size: Int) {
        resetConstraints()

        val random = Random(seed)
        do {
            val constraint = generateConstraint(random, size)
            addConstraint(constraint)
            solver.checkConstraints(seed)
        } while (!constraints.isContradicting)
    }

    private fun generateConstraint(random: Random, size: Int): UBoolExpr {
        val lhsTerms = sumTerms(List(size) { generateTerm(random) })
        val rhsTerms = sumTerms(List(size) { generateTerm(random) })

        val operations = listOf(
            ctx.mkBvSignedLessExprNoSimplify(lhsTerms, rhsTerms),
            ctx.mkBvSignedLessOrEqualExprNoSimplify(lhsTerms, rhsTerms),
            ctx.mkBvSignedGreaterExprNoSimplify(lhsTerms, rhsTerms),
            ctx.mkBvSignedGreaterOrEqualExprNoSimplify(lhsTerms, rhsTerms),
            ctx.mkEqNoSimplify(lhsTerms, rhsTerms)
        )

        val operation = operations.random(random)

        return if (random.nextBoolean()) {
            ctx.mkNotNoSimplify(operation)
        } else {
            operation
        }
    }

    private fun sumTerms(terms: List<UExpr<UBvSort>>): UExpr<UBvSort> =
        terms.reduce { acc, term -> ctx.mkBvAddExpr(acc, term) }

    private fun generateTerm(random: Random): UExpr<UBvSort> {
        val expr = if (random.nextBoolean()) {
            val value = random.nextInt(0, powerOfTwo(bvSort.sizeBits).toInt())
            ctx.mkBv(value, bvSort)
        } else {
            val names = listOf("a", "b", "c")
            val name = names.random(random)
            ctx.mkConst(name, bvSort)
        }

        return if (random.nextBoolean()) {
            ctx.mkBvNegationExpr(expr)
        } else {
            expr
        }
    }

    private fun addConstraint(expr: UBoolExpr) {
        previousConstraints = constraints.clone(ownership, MutabilityOwnership())
        constraints.addConstraint(expr)
        unsimplifiedConstraints.add(expr)
    }

    private fun UNumericConstraints<*>.addConstraint(expr: UBoolExpr) {
        if (expr is UNotExpr) {
            addNegatedNumericConstraint(expr.arg)
        } else {
            addNumericConstraint(expr)
        }
    }

    private fun KSolver<*>.checkConstraints(seed: Int) = try {
        push()

        val solvedNumericConstraints = constraints.constraints().toList()

        assertTrue(
            solvedNumericConstraints.size <= unsimplifiedConstraints.size,
            "Too many constraint on seed $seed"
        )

        val actualConstraints = ctx.mkAnd(solvedNumericConstraints)

        val expectedConstraints = ctx.mkAnd(unsimplifiedConstraints)

        assert(ctx.mkNot(ctx.mkEq(actualConstraints, expectedConstraints)))

        val status = check()
//        if (status == KSolverStatus.SAT) {
//            debugFailedStatement()
//        }

        assertEquals(KSolverStatus.UNSAT, status, "Failed on $seed")
    } finally {
        pop()
    }

    private fun KSolver<*>.debugFailedStatement() {
        val model = model()
        val failedStatements = unsimplifiedConstraints.filter {
            model.eval(it, false).isFalse
        }
        val lastExpr = unsimplifiedConstraints.last()

        logger.error { "Incorrect state after add: $lastExpr" }
        logger.error { "Unsatisfied statements: $failedStatements" }

        previousConstraints?.addConstraint(lastExpr)
    }

    private inline fun repeatWithRandom(times: Int, body: (Int) -> Unit) {
        val logStep = times / 100 + 1
        repeat(times) {
            if (it % logStep == 0) {
                logger.debug { "$it / $times" }
            }
            body(Random.nextInt())
        }
    }

    companion object {
        const val RANDOM_TEST_1_ITERATIONS = 100//100_000
        const val RANDOM_TEST_2_ITERATIONS = 100//1_000_000
        const val RANDOM_TEST_3_ITERATIONS = 100//1_000_000
        const val RANDOM_TEST_4_ITERATIONS = 50//1_000_000
    }
}
