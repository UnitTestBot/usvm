package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class ArraysMethods : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "arrays")

    @Test
    fun testShiftMethod() {
        val method = getMethod(className, "shiftMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r -> x.value && r is TsTestValue.TsNumber && r.number == 1.0 },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(2.0, 3.0)
            },
        )
    }

    @Test
    fun testUnshiftMethod() {
        val method = getMethod(className, "unshiftMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r -> x.value && r is TsTestValue.TsNumber && r.number == 0.0 },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(0.0, 1.0, 2.0, 3.0)
            },
        )
    }

    @Test
    fun testPopMethod() {
        val method = getMethod(className, "popMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r -> x.value && r is TsTestValue.TsNumber && r.number == 3.0 },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(1.0, 2.0)
            },
        )
    }

    @Test
    fun testPushMethod() {
        val method = getMethod(className, "pushMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r -> x.value && r is TsTestValue.TsNumber && r.number == 4.0 },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(1.0, 2.0, 3.0, 4.0)
            },
        )
    }

    @Test
    fun testFillMethod() {
        val method = getMethod(className, "fillMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r -> x.value && r is TsTestValue.TsNumber && r.number == 7.0 },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.all { (it as TsTestValue.TsNumber).number == 7.0 }
            },
        )
    }
}
