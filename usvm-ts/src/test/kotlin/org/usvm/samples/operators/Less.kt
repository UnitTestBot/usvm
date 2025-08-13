package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.toDouble
import kotlin.test.Test

class Less : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/Less.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun testLessNumbers() {
        val method = getMethod("lessNumbers")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, b, r -> a.number < b.number && (r eq a) },
            { a, b, r -> b.number < a.number && (r eq b) },
            { a, b, r -> a.number == b.number && (r eq 0) },
        )
    }

    @Test
    fun testLessBooleans() {
        val method = getMethod("lessBooleans")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsBoolean>(
            method,
            { a, b, r -> !a.value && b.value && !r.value },
            { a, b, r -> a.value && !b.value && !r.value },
            { a, b, r -> a.value == b.value && !r.value },
        )
    }

    @Test
    fun testLessMixed() {
        val method = getMethod("lessMixed")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, b, r -> a.number < b.value.toDouble() && (r eq a) },
            { a, b, r -> b.value.toDouble() < a.number && (r.number == b.value.toDouble()) },
            { a, b, r -> a.number == b.value.toDouble() && (r eq 0) },
        )
    }

    @Test
    fun testLessRefs() {
        val method = getMethod("lessRefs")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsClass, TsTestValue.TsBoolean>(
            method,
        )
    }

    @Test
    fun testLessUnknown() {
        val method = getMethod("lessUnknown")
        discoverProperties<TsTestValue.TsUnknown, TsTestValue.TsUnknown, TsTestValue.TsBoolean>(
            method,
        )
    }
}
