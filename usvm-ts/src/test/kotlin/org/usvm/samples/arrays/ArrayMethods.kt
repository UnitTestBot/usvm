package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class ArrayMethods : TsMethodTestRunner() {
    private val tsPath = "/samples/arrays/ArrayMethods.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun testArrayPush() {
        val method = getMethod("arrayPush")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsNumber && (r.number == 4.0)
            },
            { x, r ->
                !x.value && r is TsTestValue.TsArray<*> &&
                    r.values.map { (it as TsTestValue.TsNumber).number } == listOf(10.0, 20.0, 30.0, 5.0)
            },
        )
    }

    @Test
    fun testArrayPushIntoNumber() {
        val method = getMethod("arrayPushIntoNumber")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsArray<*>>(
            method = method,
            { x, r -> x.values.size == r.values.size - 1 && (r.values.last() as TsTestValue.TsNumber).number == 123.0 }
        )
    }

    @Test
    fun testArrayPushIntoUnknown() {
        val method = getMethod("arrayPushIntoUnknown")
        discoverProperties<TsTestValue.TsArray<*>, TsTestValue.TsArray<*>>(
            method = method,
            { x, r -> x.values.size == r.values.size - 1 && (r.values.last() as TsTestValue.TsNumber).number == 123.0 }
        )
    }

    @Test
    fun testArrayPop() {
        val method = getMethod("arrayPop")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsNumber && (r.number == 30.0)
            },
            { x, r ->
                !x.value && r is TsTestValue.TsArray<*> &&
                    r.values.map { (it as TsTestValue.TsNumber).number } == listOf(10.0, 20.0)
            },
        )
    }

    @Test
    fun testArrayFill() {
        val method = getMethod("arrayFill")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            // Note: both branches return the same array (filled and modified original).
            { x, r ->
                x.value && r.values.map { it.number } == listOf(7.0, 7.0, 7.0)
            },
            { x, r ->
                !x.value && r.values.map { it.number } == listOf(7.0, 7.0, 7.0)
            },
        )
    }

    @Test
    fun testArrayShift() {
        val method = getMethod("arrayShift")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsNumber && (r.number == 10.0)
            },
            { x, r ->
                !x.value && r is TsTestValue.TsArray<*> &&
                    r.values.map { (it as TsTestValue.TsNumber).number } == listOf(20.0, 30.0)
            },
        )
    }

    @Test
    fun testArrayUnshift() {
        val method = getMethod("arrayUnshift")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsNumber
                    && (r.number == 4.0)
            },
            { x, r ->
                !x.value && r is TsTestValue.TsArray<*>
                    && r.values.map { (it as TsTestValue.TsNumber).number } == listOf(5.0, 10.0, 20.0, 30.0)
            },
        )
    }

    @Test
    fun testArrayJoin() {
        val method = getMethod("arrayJoin")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsString
                    && (r.value == "joined_array_result")
            },
            { x, r ->
                !x.value && r is TsTestValue.TsString
                    && (r.value == "joined_array_result")
            },
        )
    }

    @Test
    fun testArraySlice() {
        val method = getMethod("arraySlice")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsArray<*>
                // Note: In symbolic execution, the exact slice result may be symbolic
            },
            { x, r ->
                !x.value && r is TsTestValue.TsArray<*>
                // Note: In symbolic execution, the exact slice result may be symbolic
            },
        )
    }

    @Test
    fun testArrayConcat() {
        val method = getMethod("arrayConcat")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                // if (x) then [1, 2, 3, 4]
                x.value && r is TsTestValue.TsArray<*> &&
                    r.values.map { (it as TsTestValue.TsNumber).number } == listOf(1.0, 2.0, 3.0, 4.0)
            },
            { x, r ->
                // if (!x) then [1, 2]
                !x.value && r is TsTestValue.TsArray<*> &&
                    r.values.map { (it as TsTestValue.TsNumber).number } == listOf(1.0, 2.0)
            },
        )
    }

    @Test
    fun testArrayIndexOf() {
        val method = getMethod("arrayIndexOf")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsNumber
                // Result is symbolic - could be valid index or -1
            },
            { x, r ->
                !x.value && r is TsTestValue.TsNumber
                // Result is symbolic - could be valid index or -1
            },
        )
    }

    @Test
    fun testArrayIncludes() {
        val method = getMethod("arrayIncludes")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { x, r ->
                x.value && r is TsTestValue.TsBoolean
                // Result is symbolic - could be true or false
            },
            { x, r ->
                !x.value && r is TsTestValue.TsBoolean
                // Result is symbolic - could be true or false
            },
        )
    }

    @Test
    fun testArrayReverse() {
        val method = getMethod("arrayReverse")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            // Note: Both branches return the same array (reversed and modified original).
            { x, r ->
                x.value && r is TsTestValue.TsArray<*> &&
                    r.values.map { (it as TsTestValue.TsNumber).number } == listOf(3.0, 2.0, 1.0)
            },
            { x, r ->
                !x.value && r is TsTestValue.TsArray<*> &&
                    r.values.map { (it as TsTestValue.TsNumber).number } == listOf(3.0, 2.0, 1.0)
            },
        )
    }
}
