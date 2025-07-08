package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.toDouble
import kotlin.test.Test

class Less : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "operators")

    @Test
    fun testLessNumbers() {
        val method = getMethod(className, "lessNumbers")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method,
            { a, b, r -> a.number < b.number && r.number == a.number },
            { a, b, r -> b.number < a.number && r.number == b.number },
            { a, b, r -> a.number == b.number && r.number == 0.0 },
        )
    }

    @Test
    fun testLessBooleans() {
        val method = getMethod(className, "lessBooleans")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsBoolean>(
            method,
            { a, b, r -> !a.value && b.value && !r.value },
            { a, b, r -> a.value && !b.value && !r.value },
            { a, b, r -> a.value == b.value && !r.value },
        )
    }

    @Test
    fun testLessMixed() {
        val method = getMethod(className, "lessMixed")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method,
            { a, b, r -> a.number < b.value.toDouble() && r.number == a.number },
            { a, b, r -> b.value.toDouble() < a.number && r.number == b.value.toDouble() },
            { a, b, r -> a.number == b.value.toDouble() && r.number == 0.0 },
        )
    }

    @Test
    fun testLessRefs() {
        val method = getMethod(className, "lessRefs")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsClass, TsTestValue.TsBoolean>(
            method,
        )
    }

    @Test
    fun testLessUnknown() {
        val method = getMethod(className, "lessUnknown")
        discoverProperties<TsTestValue.TsUnknown, TsTestValue.TsUnknown, TsTestValue.TsBoolean>(
            method,
        )
    }
}
