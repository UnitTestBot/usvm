package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class ArraysMethods : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "arrays")

    @Test
    fun testPushMethod() {
        val method = getMethod(className, "pushMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value
                    && r is TsTestValue.TsNumber
                    && (r.number == 4.0)
            },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(10.0, 20.0, 30.0, 5.0)
            },
        )
    }

    @Test
    fun testPopMethod() {
        val method = getMethod(className, "popMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value
                    && r is TsTestValue.TsNumber
                    && (r.number == 30.0)
            },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(10.0, 20.0)
            },
        )
    }

    @Test
    fun testFillMethod() {
        val method = getMethod(className, "fillMethod")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(7.0, 7.0, 7.0) },
        )
    }

    @Test
    fun testShiftMethod() {
        val method = getMethod(className, "shiftMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value
                    && r is TsTestValue.TsNumber
                    && (r.number == 10.0) },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(20.0, 30.0)
            },
        )
    }

    @Test
    fun testUnshiftMethod() {
        val method = getMethod(className, "unshiftMethod")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r -> x.value && r is TsTestValue.TsNumber && r.number == 4.0 },
            { x, r ->
                !x.value
                    && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(3.0, 2.0, 9.0, 7.0)
            },
        )
    }
}
