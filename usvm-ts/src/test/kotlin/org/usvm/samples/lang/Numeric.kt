package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.isNaN

class Numeric : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Numeric.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test numberToNumber`() {
        val method = getMethod("numberToNumber")
        // ```ts
        // if (x != x) return Number(x) // NaN
        // if (x == 0) return Number(x) // 0
        // if (x > 0) return Number(x) // x (>0)
        // if (x < 0) return Number(x) // x (<0)
        // ```
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.isNaN() && r.isNaN() },
            { x, r -> (x eq 0) && (r eq 0) },
            { x, r -> (x.number > 0) && (r eq x) },
            { x, r -> (x.number < 0) && (r eq x) },
        )
    }

    @Test
    fun `test boolToNumber`() {
        val method = getMethod("boolToNumber")
        // ```ts
        // if (x) return Number(x) // 1
        // if (!x) return Number(x) // 0
        // ```
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { x, r -> x.value && (r eq 1) },
            { x, r -> !x.value && (r eq 0) },
        )
    }

    @Test
    fun `test undefinedToNumber`() {
        val method = getMethod("undefinedToNumber")
        // ```ts
        // return Number(undefined) // NaN
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.isNaN() },
        )
    }

    @Test
    fun `test nullToNumber`() {
        val method = getMethod("nullToNumber")
        // ```ts
        // return Number(null) // 0
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 0 },
        )
    }

    @Disabled("Strings are not supported")
    @Test
    fun `test emptyStringToNumber`() {
        val method = getMethod("emptyStringToNumber")
        // ```ts
        // return Number("") // 0
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 0 },
        )
    }

    @Disabled("Strings are not supported")
    @Test
    fun `test numberStringToNumber`() {
        val method = getMethod("numberStringToNumber")
        // ```ts
        // return Number("42") // 42
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
        )
    }

    @Disabled("Strings are not supported")
    @Test
    fun `test stringToNumber`() {
        val method = getMethod("stringToNumber")
        // ```ts
        // return Number("hello") // NaN
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.isNaN() },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test emptyArrayToNumber`() {
        val method = getMethod("emptyArrayToNumber")
        // ```ts
        // return Number([]) // 0
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 0 },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test singleNumberArrayToNumber`() {
        val method = getMethod("singleNumberArrayToNumber")
        // ```ts
        // return Number([42]) // 42
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test singleUndefinedArrayToNumber`() {
        val method = getMethod("singleUndefinedArrayToNumber")
        // ```ts
        // return Number([undefined]) // 0
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 0 },
        )
    }

    @Disabled("Could not resolve unique constructor of anonymous class")
    @Test
    fun `test singleObjectArrayToNumber`() {
        val method = getMethod("singleObjectArrayToNumber")
        // ```ts
        // return Number([{}]) // NaN
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.isNaN() },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test singleCustomFortyTwoObjectArrayToNumber`() {
        val method = getMethod("singleCustomFortyTwoObjectArrayToNumber")
        // ```ts
        // return Number([new FortyTwo()]) // 42
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test multipleElementArrayToNumber`() {
        val method = getMethod("multipleElementArrayToNumber")
        // ```ts
        // return Number([5, 100]) // NaN
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.isNaN() },
        )
    }

    @Disabled("Could not resolve unique constructor of anonymous class")
    @Test
    fun `test emptyObjectToNumber`() {
        val method = getMethod("emptyObjectToNumber")
        // ```ts
        // return Number({}) // NaN
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.isNaN() },
        )
    }

    @Disabled("Could not resolve unique constructor of anonymous class")
    @Test
    fun `test objectToNumber`() {
        val method = getMethod("objectToNumber")
        // ```ts
        // return Number({a: 42}) // NaN
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.isNaN() },
        )
    }

    @Disabled("Could not resolve unique FortyTwo::constructor")
    @Test
    fun `test customFortyTwoObjectToNumber`() {
        val method = getMethod("customFortyTwoObjectToNumber")
        // ```ts
        // return Number(new FortyTwo()) // 42
        // ```
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
        )
    }
}
