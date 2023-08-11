package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


class CopyOfExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("class org.jacodb.impl.types.JcArrayTypeImpl cannot be cast to class org.jacodb.api.JcClassType")
    fun testCopyOf() {
        checkDiscoveredPropertiesWithExceptions(
            CopyOfExample::copyOfExample,
            ignoreNumberOfAnalysisResults,
            { _, _, l, r -> l < 0 && r.isException<NegativeArraySizeException>() },
            { _, arr, l, r -> arr.copyOf(l).contentEquals(r.getOrThrow()) },
        )
    }

    @Test
    @Disabled("class org.jacodb.impl.types.JcArrayTypeImpl cannot be cast to class org.jacodb.api.JcClassType")
    fun testCopyOfRange() {
        checkDiscoveredPropertiesWithExceptions(
            CopyOfExample::copyOfRangeExample,
            ignoreNumberOfAnalysisResults,
            { _, _, from, _, r -> from < 0 && r.isException<ArrayIndexOutOfBoundsException>() },
            { _, arr, from, _, r -> from > arr.size && r.isException<ArrayIndexOutOfBoundsException>() },
            { _, _, from, to, r -> from > to && r.isException<IllegalArgumentException>() },
            { _, arr, from, to, r -> arr.copyOfRange(from, to).contentEquals(r.getOrThrow()) },
        )
    }
}