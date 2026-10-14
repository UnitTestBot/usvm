package org.usvm.solver

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KBoolSort
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.usvm.SolverType
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ObservedSolverTest {
    @ParameterizedTest
    @EnumSource(SolverType::class)
    fun `real solvers report every ordinary and assumption check`(solverType: SolverType) {
        val events = mutableListOf<USolverQueryEvent>()
        KContext().use { ctx ->
            val solver = when (solverType) {
                SolverType.YICES -> KYicesSolver(ctx)
                SolverType.Z3 -> KZ3Solver(ctx)
            }
            observeSolverQueries(solver = solver, observer = { event -> events += event }).use { observed ->
                val initial = observed.check(timeout = 2.seconds)
                observed.push()
                observed.assert(ctx.falseExpr)
                val asserted = observed.check(timeout = 2.seconds)
                observed.pop(n = 1u)
                val assumed = observed.checkWithAssumptions(assumptions = listOf(ctx.falseExpr), timeout = 2.seconds)
                val final = observed.check(timeout = 2.seconds)

                val expected = listOf(KSolverStatus.SAT, KSolverStatus.UNSAT, KSolverStatus.UNSAT, KSolverStatus.SAT)
                assertEquals(expected = expected, actual = listOf(initial, asserted, assumed, final))
                assertEquals(expected = expected, actual = events.map { it.status })
                assertTrue(actual = events.all { it.failure == null && it.elapsedNanos >= 0 })
            }
        }
    }

    @ParameterizedTest
    @EnumSource(KSolverStatus::class)
    fun `ordinary checks preserve status and timeout`(status: KSolverStatus) {
        val solver = mockk<KSolver<KSolverConfiguration>>()
        val events = mutableListOf<USolverQueryEvent>()
        every { solver.check(timeout = 2.seconds) } returns status
        val observed = observeSolverQueries(solver = solver, observer = { event -> events += event })

        val result = observed.check(timeout = 2.seconds)

        assertEquals(expected = status, actual = result)
        assertEquals(expected = status, actual = events.single().status)
        assertNull(actual = events.single().failure)
        verify(exactly = 1) { solver.check(timeout = 2.seconds) }
    }

    @ParameterizedTest
    @EnumSource(KSolverStatus::class)
    fun `assumption checks preserve assumptions status and timeout`(status: KSolverStatus) {
        val solver = mockk<KSolver<KSolverConfiguration>>()
        val assumptions = listOf(mockk<KExpr<KBoolSort>>())
        val events = mutableListOf<USolverQueryEvent>()
        every { solver.checkWithAssumptions(assumptions = any(), timeout = 2.seconds) } answers {
            assertSame(expected = assumptions, actual = firstArg())
            status
        }
        val observed = observeSolverQueries(solver = solver, observer = { event -> events += event })

        val result = observed.checkWithAssumptions(assumptions = assumptions, timeout = 2.seconds)

        assertEquals(expected = status, actual = result)
        assertEquals(expected = status, actual = events.single().status)
        assertNull(actual = events.single().failure)
        verify(exactly = 1) { solver.checkWithAssumptions(assumptions = assumptions, timeout = 2.seconds) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `failed checks report one event and rethrow the same exception`(useAssumptions: Boolean) {
        val solver = mockk<KSolver<KSolverConfiguration>>()
        val failure = IllegalStateException("backend failure")
        val events = mutableListOf<USolverQueryEvent>()
        every { solver.check(timeout = any()) } throws failure
        every { solver.checkWithAssumptions(assumptions = any(), timeout = any()) } throws failure
        val observed = observeSolverQueries(solver = solver, observer = { event -> events += event })

        val caught = assertFailsWith<IllegalStateException> {
            if (useAssumptions) {
                observed.checkWithAssumptions(assumptions = emptyList(), timeout = 2.seconds)
            } else {
                observed.check(timeout = 2.seconds)
            }
        }

        assertSame(expected = failure, actual = caught)
        assertNull(actual = events.single().status)
        assertEquals(expected = failure::class.java.name, actual = events.single().failure)
        assertTrue(actual = events.single().elapsedNanos >= 0)
    }

    @Test
    fun `observer failure does not replace a solver result`() {
        val solver = mockk<KSolver<KSolverConfiguration>>()
        val events = mutableListOf<USolverQueryEvent>()
        every { solver.check(timeout = any()) } returns KSolverStatus.SAT
        val observed = observeSolverQueries(
            solver = solver,
            observer = { event ->
                events += event
                error("observer failure")
            },
        )

        val result = observed.check(timeout = 2.seconds)

        assertEquals(expected = KSolverStatus.SAT, actual = result)
        assertEquals(expected = KSolverStatus.SAT, actual = events.single().status)
        assertNull(actual = events.single().failure)
    }

    @Test
    fun `observer failure does not replace a backend exception`() {
        val solver = mockk<KSolver<KSolverConfiguration>>()
        val failure = IllegalStateException("backend failure")
        val events = mutableListOf<USolverQueryEvent>()
        every { solver.check(timeout = any()) } throws failure
        val observed = observeSolverQueries(
            solver = solver,
            observer = { event ->
                events += event
                throw IllegalArgumentException("observer failure")
            },
        )

        val caught = assertFailsWith<IllegalStateException> { observed.check(timeout = 2.seconds) }

        assertSame(expected = failure, actual = caught)
        assertNull(actual = events.single().status)
        assertEquals(expected = failure::class.java.name, actual = events.single().failure)
    }

    @Test
    fun `each retry has its own elapsed time excluding observer work`() {
        val solver = mockk<KSolver<KSolverConfiguration>>()
        val clock = TestTimeSource()
        val events = mutableListOf<USolverQueryEvent>()
        var checks = 0
        every { solver.checkWithAssumptions(assumptions = any(), timeout = any()) } answers {
            checks++
            clock += 3.milliseconds
            if (checks == 1) KSolverStatus.UNSAT else KSolverStatus.SAT
        }
        val observed = ObservedSolver(
            solver = solver,
            observer = { event ->
                events += event
                clock += 10.seconds
            },
            timeSource = clock,
        )

        val first = observed.checkWithAssumptions(assumptions = emptyList(), timeout = 2.seconds)
        val second = observed.checkWithAssumptions(assumptions = emptyList(), timeout = 2.seconds)

        assertEquals(expected = KSolverStatus.UNSAT, actual = first)
        assertEquals(expected = KSolverStatus.SAT, actual = second)
        assertEquals(expected = listOf(KSolverStatus.UNSAT, KSolverStatus.SAT), actual = events.map { it.status })
        assertEquals(expected = listOf(3_000_000L, 3_000_000L), actual = events.map { it.elapsedNanos })
        verify(exactly = 2) { solver.checkWithAssumptions(assumptions = emptyList(), timeout = 2.seconds) }
    }

    @Test
    fun `assertions model retrieval and lifecycle are delegated without query events`() {
        val solver = mockk<KSolver<KSolverConfiguration>>()
        val assertion = mockk<KExpr<KBoolSort>>()
        val model = mockk<KModel>()
        val events = mutableListOf<USolverQueryEvent>()
        every { solver.push() } just Runs
        every { solver.assert(assertion) } just Runs
        every { solver.model() } returns model
        every { solver.pop(n = 1u) } just Runs
        every { solver.close() } just Runs
        val observed = observeSolverQueries(solver = solver, observer = { event -> events += event })

        observed.push()
        observed.assert(assertion)
        val result = observed.model()
        observed.pop(n = 1u)
        observed.close()

        assertSame(expected = model, actual = result)
        assertTrue(actual = events.isEmpty())
        verify(exactly = 1) {
            solver.push()
            solver.assert(assertion)
            solver.model()
            solver.pop(n = 1u)
            solver.close()
        }
    }
}
