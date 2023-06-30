package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

internal class IntArrayBasicsTest : JavaMethodTestRunner() {
    @Test
    fun testInitArray() {
        checkDiscoveredPropertiesWithExceptions(
            IntArrayBasics::initAnArray,
            ignoreNumberOfAnalysisResults,
            { _, n, r -> n < 0 && r.isException<NegativeArraySizeException>() },
            { _, n, r -> n == 0 && r.isException<IndexOutOfBoundsException>() },
            { _, n, r -> n == 1 && r.isException<IndexOutOfBoundsException>() },
            { _, n, r ->
                val resultArray = IntArray(n) { if (it == n - 1 || it == n - 2) it else 0 }
                n > 1 && r.getOrNull() contentEquals resultArray
            }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testIsValid() {
        checkDiscoveredPropertiesWithExceptions(
            IntArrayBasics::isValid,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, n, r -> a != null && (n < 0 || n >= a.size) && r.isException<IndexOutOfBoundsException>() },
            { _, a, n, r -> a != null && n in a.indices && a[n] == 9 && n == 5 && r.getOrNull() == true },
            { _, a, n, r -> a != null && n in a.indices && !(a[n] == 9 && n == 5) && r.getOrNull() == false },
            { _, a, n, r -> a != null && n in a.indices && a[n] > 9 && n == 5 && r.getOrNull() == true },
            { _, a, n, r -> a != null && n in a.indices && !(a[n] > 9 && n == 5) && r.getOrNull() == false },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testGetValue() {
        checkDiscoveredPropertiesWithExceptions(
            IntArrayBasics::getValue,
            ge(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, n, r -> a != null && a.size > 6 && (n < 0 || n >= a.size) && r.isException<IndexOutOfBoundsException>() },
            { _, a, n, r -> a != null && a.size > 6 && n < a.size && r.getOrNull() == a[n] },
            { _, a, _, r -> a != null && a.size <= 6 && r.getOrNull() == -1 }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testSetValue() {
        checkDiscoveredPropertiesWithExceptions(
            IntArrayBasics::setValue,
            ignoreNumberOfAnalysisResults,
            { _, _, x, r -> x <= 0 && r.getOrNull() == 0 },
            { _, a, x, r -> x > 0 && a == null && r.isException<NullPointerException>() },
            { _, a, x, r -> x > 0 && a != null && a.isEmpty() && r.getOrNull() == 0 },
            { _, a, x, r -> x in 1..2 && a != null && a.isNotEmpty() && r.getOrNull() == 1 },
            { _, a, x, r -> x > 2 && a != null && a.isNotEmpty() && r.getOrNull() == 2 }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testCheckFour() {
        checkDiscoveredPropertiesWithExceptions(
            IntArrayBasics::checkFour,
            ignoreNumberOfAnalysisResults,
            { _, a, r -> a == null && r.isException<NullPointerException>() },
            { _, a, r -> a != null && a.size < 4 && r.getOrNull() == -1 },
            { _, a, r -> a != null && a.size >= 4 && a[0] != 1 && r.getOrNull() == 0 },
            { _, a, r -> a != null && a.size >= 4 && a[0] == 1 && a[1] != 2 && r.getOrNull() == 0 },
            { _, a, r -> a != null && a.size >= 4 && a[0] == 1 && a[1] == 2 && a[2] != 3 && r.getOrNull() == 0 },
            { _, a, r -> a != null && a.size >= 4 && a[0] == 1 && a[1] == 2 && a[2] == 3 && a[3] != 4 && r.getOrNull() == 0 },
            { _, a, r ->
                require(a != null)

                val precondition = a.size >= 4 && a[0] == 1 && a[1] == 2 && a[2] == 3 && a[3] == 4
                val postcondition = r.getOrNull() == IntArrayBasics().checkFour(a)

                precondition && postcondition
            }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testNullability() {
        checkDiscoveredProperties(
            IntArrayBasics::nullability,
            eq(3),
            { _, a, r -> a == null && r == 1 },
            { _, a, r -> a != null && a.size > 1 && r == 2 },
            { _, a, r -> a != null && a.size in 0..1 && r == 3 },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1, 2]. ")
    fun testEquality() {
        checkDiscoveredProperties(
            IntArrayBasics::equality,
            eq(4),
            { _, a, _, r -> a == null && r == 1 },
            { _, a, b, r -> a != null && b == null && r == 1 },
            { _, a, b, r -> a != null && b != null && a.size == b.size && r == 2 },
            { _, a, b, r -> a != null && b != null && a.size != b.size && r == 3 },
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testOverwrite() {
        checkDiscoveredPropertiesWithExceptions(
            IntArrayBasics::overwrite,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 1 && r.getOrNull() == 0 },
            { _, a, b, r -> a != null && a.size == 1 && a[0] > 0 && b < 0 && r.getOrNull() == 1 },
            { _, a, b, r -> a != null && a.size == 1 && a[0] > 0 && b >= 0 && r.getOrNull() == 2 },
            { _, a, _, r -> a != null && a.size == 1 && a[0] <= 0 && r.getOrNull() == 3 },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [4, 5]")
    fun testMergeArrays() {
        checkDiscoveredProperties(
            IntArrayBasics::mergeArrays,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, _ -> fst == null },
            { _, fst, snd, _ -> fst != null && snd == null },
            { _, fst, snd, r -> fst != null && snd != null && fst.size < 2 && r == null },
            { _, fst, snd, r -> fst != null && snd != null && fst.size >= 2 && snd.size < 2 && r == null },
            { _, fst, snd, r ->
                require(fst != null && snd != null && r != null)

                val sizeConstraint = fst.size >= 2 && snd.size >= 2 && r.size == fst.size + snd.size
                val maxConstraint = fst.maxOrNull()!! < snd.maxOrNull()!!
                val contentConstraint = r contentEquals (IntArrayBasics().mergeArrays(fst, snd))

                sizeConstraint && maxConstraint && contentConstraint
            },
            { _, fst, snd, r ->
                require(fst != null && snd != null && r != null)

                val sizeConstraint = fst.size >= 2 && snd.size >= 2 && r.size == fst.size + snd.size
                val maxConstraint = fst.maxOrNull()!! >= snd.maxOrNull()!!
                val contentConstraint = r contentEquals (IntArrayBasics().mergeArrays(fst, snd))

                sizeConstraint && maxConstraint && contentConstraint
            }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testNewArrayInTheMiddle() {
        checkDiscoveredProperties(
            IntArrayBasics::newArrayInTheMiddle,
            ignoreNumberOfAnalysisResults,
            { _, a, _ -> a == null },
            { _, a, _ -> a != null && a.isEmpty() },
            { _, a, _ -> a != null && a.size < 2 },
            { _, a, _ -> a != null && a.size < 3 },
            { _, a, r -> a != null && a.size >= 3 && r != null && r[0] == 1 && r[1] == 2 && r[2] == 3 }
        )
    }

    // TODO unsupported matchers
//    @Test
//    fun testNewArrayInTheMiddleMutation() {
//        checkParamsMutations(
//            IntArrayBasics::newArrayInTheMiddle,
//            ignoreNumberOfAnalysisResults,
//            { _, arrayAfter -> arrayAfter[0] == 1 && arrayAfter[1] == 2 && arrayAfter[2] == 3 }
//        )
//    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testReversed() {
        checkDiscoveredProperties(
            IntArrayBasics::reversed,
            ignoreNumberOfAnalysisResults,
            { _, a, _ -> a == null },
            { _, a, _ -> a != null && a.size != 3 },
            { _, a, r -> a != null && a.size == 3 && a[0] <= a[1] && r == null },
            { _, a, r -> a != null && a.size == 3 && a[0] > a[1] && a[1] <= a[2] && r == null },
            { _, a, r -> a != null && a.size == 3 && a[0] > a[1] && a[1] > a[2] && r contentEquals a.reversedArray() },
        )
    }

    @Test
    @Disabled("Sequence is empty.")
    fun testUpdateCloned() {
        checkDiscoveredProperties(
            IntArrayBasics::updateCloned,
            eq(3),
            { _, a, _ -> a == null },
            { _, a, _ -> a.size != 3 },
            { _, a, r -> a.size == 3 && r != null && r.toList() == a.map { it * 2 } },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@42ac84a9")
    fun testArraysEqualsExample() {
        checkDiscoveredProperties(
            IntArrayBasics::arrayEqualsExample,
            eq(2),
            { _, a, r -> a.size == 3 && a contentEquals intArrayOf(1, 2, 3) && r == 1 },
            { _, a, r -> !(a contentEquals intArrayOf(1, 2, 3)) && r == 2 }
        )
    }
}