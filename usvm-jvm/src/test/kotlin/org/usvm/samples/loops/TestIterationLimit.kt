package org.usvm.samples.loops

import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import kotlin.test.Test

class TestIterationLimit : JavaMethodTestRunner() {

    override var options: UMachineOptions = super.options.copy(
        stopOnCoverage = -1,
        stateCollectionStrategy = StateCollectionStrategy.ALL,
    )

    @Test
    fun `test concrete bound no limit`() {
        checkDiscoveredProperties(
            Loops::loopWithConcreteBound,
            eq(1),
            { _, r -> r == 45 },
        )
    }

    @Test
    fun `test symbolic bound no limit`() {
        checkDiscoveredProperties(
            Loops::loopWithSymbolicBound,
            eq(10 + 1), // 10 iterations + 1 for n == 0,
            { n, r -> n == 0 && r == 0 },
            *Array(10) { nValue -> { n, r -> n == nValue + 1 && r == (0 until n).sum() } }
        )
    }

    @Test
    fun `test concrete bound with symbolic branching no limit`() {
        checkDiscoveredProperties(
            Loops::loopWithConcreteBoundAndSymbolicBranching,
            eq(2),
            { cond, r -> !cond && r == 0 },
            { cond, r -> cond && r == sumEvenNumbers(10) },
        )
    }

    @Test
    fun `test symbolic bound with symbolic branching no limit`() {
        checkDiscoveredProperties(
            Loops::loopWithSymbolicBoundAndSymbolicBranching,
            eq(10 * 2 + 1), // 10 iterations * 2 conditions + 1 for n == 0,
            { n, _, r -> n == 0 && r == 0 },
            *Array(10) { nValue -> { n, cond, r -> !cond && n == nValue + 1 && r == 0 } },
            *Array(10) { nValue -> { n, cond, r -> cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
        )
    }

    @Test
    fun `test symbolic bound with complex flow no limit`() {
        checkDiscoveredProperties(
            Loops::loopWithSymbolicBoundAndComplexControlFlow,
            eq(10 + 4 + 1), // 10 iterations + 4 for condition (stop on i == 3) + 1 for n == 0,
            { n, _, r -> n == 0 && r == 0 },
            *Array(10) { nValue -> { n, cond, r -> !cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
            *Array(3) { nValue -> { n, cond, r -> cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
            { n, cond, r -> cond && n > 3 && r == sumEvenNumbers(3) }
        )
    }

    @Test
    fun `test concrete bound limited`() {
        withOptions(options.copy(loopIterationLimit = 2)) {
            checkDiscoveredProperties(
                Loops::loopWithConcreteBound,
                eq(1),
                { _, r -> r == 45 },
            )
        }
    }

    @Test
    fun `test symbolic bound limited`() {
        withOptions(options.copy(loopIterationLimit = 2)) {
            checkDiscoveredProperties(
                Loops::loopWithSymbolicBound,
                eq(2 + 1), // 2 iterations (limited) + 1 for n == 0,
                { n, r -> n == 0 && r == 0 },
                *Array(2) { nValue -> { n, r -> n == nValue + 1 && r == (0 until n).sum() } }
            )
        }
    }

    @Test
    fun `test concrete bound with symbolic branching limited`() {
        withOptions(options.copy(loopIterationLimit = 2)) {
            checkDiscoveredProperties(
                Loops::loopWithConcreteBoundAndSymbolicBranching,
                eq(2),
                { cond, r -> !cond && r == 0 },
                { cond, r -> cond && r == sumEvenNumbers(10) },
            )
        }
    }

    @Test
    fun `test symbolic bound with symbolic branching limited`() {
        withOptions(options.copy(loopIterationLimit = 2)) {
            checkDiscoveredProperties(
                Loops::loopWithSymbolicBoundAndSymbolicBranching,
                eq(2 * 2 + 1), // 2 iterations (limited) * 2 conditions + 1 for n == 0,
                { n, _, r -> n == 0 && r == 0 },
                *Array(2) { nValue -> { n, cond, r -> !cond && n == nValue + 1 && r == 0 } },
                *Array(2) { nValue -> { n, cond, r -> cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
            )
        }
    }

    @Test
    fun `test symbolic bound with complex flow limited`() {
        withOptions(options.copy(loopIterationLimit = 4)) {
            checkDiscoveredProperties(
                Loops::loopWithSymbolicBoundAndComplexControlFlow,
                eq(4 + 4 + 1), // 4 iterations (limited) + 4 for condition (stop on i == 3) + 1 for n == 0,
                { n, _, r -> n == 0 && r == 0 },
                *Array(4) { nValue -> { n, cond, r -> !cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
                *Array(3) { nValue -> { n, cond, r -> cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
                { n, cond, r -> cond && n > 3 && r == sumEvenNumbers(3) }
            )
        }
    }


    @Test
    fun `test concrete bound iterative deep no limit`() {
        withOptions(options.copy(loopIterativeDeepening = true)) {
            checkDiscoveredProperties(
                Loops::loopWithConcreteBound,
                eq(1),
                { _, r -> r == 45 },
            )
        }
    }

    @Test
    fun `test symbolic bound iterative deep no limit`() {
        withOptions(options.copy(loopIterativeDeepening = true)) {
            checkDiscoveredProperties(
                Loops::loopWithSymbolicBound,
                eq(10 + 1), // 10 iterations + 1 for n == 0,
                { n, r -> n == 0 && r == 0 },
                *Array(10) { nValue -> { n, r -> n == nValue + 1 && r == (0 until n).sum() } }
            )
        }
    }

    @Test
    fun `test concrete bound with symbolic branching iterative deep no limit`() {
        withOptions(options.copy(loopIterativeDeepening = true)) {
            checkDiscoveredProperties(
                Loops::loopWithConcreteBoundAndSymbolicBranching,
                eq(2),
                { cond, r -> !cond && r == 0 },
                { cond, r -> cond && r == sumEvenNumbers(10) },
            )
        }
    }

    @Test
    fun `test symbolic bound with symbolic branching iterative deep no limit`() {
        withOptions(options.copy(loopIterativeDeepening = true)) {
            checkDiscoveredProperties(
                Loops::loopWithSymbolicBoundAndSymbolicBranching,
                eq(10 * 2 + 1), // 10 iterations * 2 conditions + 1 for n == 0,
                { n, _, r -> n == 0 && r == 0 },
                *Array(10) { nValue -> { n, cond, r -> !cond && n == nValue + 1 && r == 0 } },
                *Array(10) { nValue -> { n, cond, r -> cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
            )
        }
    }

    @Test
    fun `test symbolic bound with complex flow iterative deep no limit`() {
        withOptions(options.copy(loopIterativeDeepening = true)) {
            checkDiscoveredProperties(
                Loops::loopWithSymbolicBoundAndComplexControlFlow,
                eq(10 + 4 + 1), // 10 iterations + 4 for condition (stop on i == 3) + 1 for n == 0,
                { n, _, r -> n == 0 && r == 0 },
                *Array(10) { nValue -> { n, cond, r -> !cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
                *Array(3) { nValue -> { n, cond, r -> cond && n == nValue + 1 && r == sumEvenNumbers(n) } },
                { n, cond, r -> cond && n > 3 && r == sumEvenNumbers(3) }
            )
        }
    }

    private fun sumEvenNumbers(n: Int) = (0 until n).filter { it % 2 == 0 }.sum()
}
