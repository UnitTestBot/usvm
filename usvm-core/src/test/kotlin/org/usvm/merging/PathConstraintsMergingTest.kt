package org.usvm.merging

import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import org.junit.jupiter.api.BeforeEach
import org.usvm.UBv32SizeExprProvider
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.constraints.UPathConstraints
import org.usvm.sizeSort
import org.usvm.solver.UExprTranslator
import org.usvm.types.single.SingleTypeSystem
import org.usvm.types.single.SingleTypeSystem.SingleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

class PathConstraintsMergingTest {
    private val typeSystem = SingleTypeSystem

    private lateinit var ctx: UContext<UBv32Sort>
    private lateinit var translator: UExprTranslator<SingleType, *>
    private lateinit var smtSolver: KZ3Solver

    @BeforeEach
    fun initializeContext() {
        ctx = UContext(UBv32SizeExprProvider)
        translator = UExprTranslator(ctx)
        smtSolver = KZ3Solver(ctx)
    }

    @Test
    fun `Empty path constraints`() = with(ctx) {
        val pcLeft = UPathConstraints.empty(ctx, typeSystem)
        val pcRight = pcLeft.clone()
        checkMergedEqualsOriginals(pcLeft, pcRight)
    }

    @Test
    fun `Equal path constraints`() = with(ctx) {
        val (pcLeft, pcRight) = buildCommonPrefix()
        checkMergedEqualsOriginals(pcLeft, pcRight)
    }

    @Test
    fun `Distinct path constraints in logical constraints`() = with(ctx) {
        val (pcLeft, pcRight) = buildCommonPrefix()
        pcLeft += (mkRegisterReading(-1, addressSort) neq mkRegisterReading(-2, addressSort)) or
            (mkRegisterReading(-1, addressSort) neq mkRegisterReading(-3, addressSort))
        pcRight += (mkRegisterReading(-1, addressSort) eq mkRegisterReading(-2, addressSort)) or
            (mkRegisterReading(-1, addressSort) eq mkRegisterReading(-3, addressSort))
        checkMergedEqualsOriginals(pcLeft, pcRight)
    }

    @Test
    fun `Distinct path constraints in numeric constraints`() = with(ctx) {
        val (pcLeft, pcRight) = buildCommonPrefix()
        pcLeft += mkBvSignedLessExpr(mkRegisterReading(-1, sizeSort), mkRegisterReading(-2, sizeSort))
        pcRight += mkBvSignedLessExpr(mkRegisterReading(-3, sizeSort), mkRegisterReading(-4, sizeSort))
        checkMergedEqualsOriginals(pcLeft, pcRight)
    }

    @Test
    fun `Distinct path constraints in type constraints`() = with(ctx) {
        val (pcLeft, pcRight) = buildCommonPrefix()
        pcLeft += mkBvSignedLessExpr(mkRegisterReading(-1, sizeSort), mkRegisterReading(-2, sizeSort))
        pcRight += mkBvSignedLessExpr(mkRegisterReading(-3, sizeSort), mkRegisterReading(-4, sizeSort))
        checkMergedEqualsOriginals(pcLeft, pcRight)
    }

    @Test
    fun `Distinct path constraints in equality constraints`(): Unit = with(ctx) {
        assertFails { // TODO: improve equality constraints merging
            val (pcLeft, pcRight) = buildCommonPrefix()
            pcLeft += mkEq(mkRegisterReading(-1, addressSort), mkRegisterReading(-2, addressSort))
            pcRight += mkEq(mkRegisterReading(-3, addressSort), mkRegisterReading(-4, addressSort))
            checkMergedEqualsOriginals(pcLeft, pcRight)
        }
    }

    private fun buildCommonPrefix(): Pair<UPathConstraints<SingleType>, UPathConstraints<SingleType>> = with(ctx) {
        val pcLeft = UPathConstraints.empty(ctx, typeSystem)

        // logical constraints
        pcLeft += (mkRegisterReading(0, sizeSort) eq mkRegisterReading(1, sizeSort)) or
            (mkRegisterReading(2, sizeSort) eq mkRegisterReading(3, sizeSort))

        // numeric constraints
        pcLeft += mkBvSignedLessExpr(mkRegisterReading(0, sizeSort), mkBv(5, sizeSort))
        pcLeft += mkBvSignedLessExpr(mkBv(0, sizeSort), mkRegisterReading(0, sizeSort))

        // equality constraints
        pcLeft += mkRegisterReading(4, addressSort) eq mkRegisterReading(5, addressSort)
        pcLeft += mkRegisterReading(6, addressSort) neq mkRegisterReading(7, addressSort)

        val pcRight = pcLeft.clone()
        return pcLeft to pcRight
    }

    private fun checkMergedEqualsOriginals(left: UPathConstraints<SingleType>, right: UPathConstraints<SingleType>) =
        with(ctx) {
            val mergeGuard = MutableMergeGuard(this)
            val result = left.mergeWith(right, mergeGuard)
            assertNotNull(result)
            result +=ctx.mkOr(mergeGuard.thisConstraint, mergeGuard.otherConstraint)
            val constraintsAreNotEqual = run {
                val leftConstraint = mkAnd(left.constraints(translator).toList())
                val rightConstraint = mkAnd(right.constraints(translator).toList())
                val mergedConstraint = mkAnd(result.constraints(translator).toList())
                (leftConstraint or rightConstraint) neq mergedConstraint
            }

            smtSolver.assert(constraintsAreNotEqual)
            val status = smtSolver.check()
            assertEquals(KSolverStatus.UNSAT, status)
        }
}