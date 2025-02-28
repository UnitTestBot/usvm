package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner

class Numeric : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test numberToNumber`() {
        val method = getMethod(className, "numberToNumber")
        // ```ts
        // if (x != x) return Number(x) // NaN
        // if (x == 0) return Number(x) // 0
        // if (x > 0) return Number(x) // x (>0)
        // if (x < 0) return Number(x) // x (<0)
        // ```
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method = method,
            { x, r -> x.number.isNaN() && r.number.isNaN() },
            { x, r -> (x.number == 0.0) && (r.number == 0.0) },
            { x, r -> (x.number > 0) && (r.number == x.number) },
            { x, r -> (x.number < 0) && (r.number == x.number) },
        )
    }

    @Test
    fun `test boolToNumber`() {
        val method = getMethod(className, "boolToNumber")
        // ```ts
        // if (x) return Number(x) // 1
        // if (!x) return Number(x) // 0
        // ```
        discoverProperties<TsValue.TsBoolean, TsValue.TsNumber>(
            method = method,
            { x, r -> x.value && (r.number == 1.0) },
            { x, r -> !x.value && (r.number == 0.0) },
        )
    }

    @Test
    fun `test undefinedToNumber`() {
        val method = getMethod(className, "undefinedToNumber")
        // ```ts
        // return Number(undefined) // NaN
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }

    @Test
    fun `test nullToNumber`() {
        val method = getMethod(className, "nullToNumber")
        // ```ts
        // return Number(null) // 0
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 0.0 },
        )
    }

    @Disabled("Strings are not supported")
    @Test
    fun `test emptyStringToNumber`() {
        val method = getMethod(className, "emptyStringToNumber")
        // ```ts
        // return Number("") // 0
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 0.0 },
        )
    }

    @Disabled("Strings are not supported")
    @Test
    fun `test numberStringToNumber`() {
        val method = getMethod(className, "numberStringToNumber")
        // ```ts
        // return Number("42") // 42
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }

    @Disabled("Strings are not supported")
    @Test
    fun `test stringToNumber`() {
        val method = getMethod(className, "stringToNumber")
        // ```ts
        // return Number("hello") // NaN
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test emptyArrayToNumber`() {
        val method = getMethod(className, "emptyArrayToNumber")
        // ```ts
        // return Number([]) // 0
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 0.0 },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test singleNumberArrayToNumber`() {
        val method = getMethod(className, "singleNumberArrayToNumber")
        // ```ts
        // return Number([42]) // 42
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test singleUndefinedArrayToNumber`() {
        val method = getMethod(className, "singleUndefinedArrayToNumber")
        // ```ts
        // return Number([undefined]) // 0
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 0.0 },
        )
    }

    @Disabled("Could not resolve unique constructor of anonymous class")
    @Test
    fun `test singleObjectArrayToNumber`() {
        val method = getMethod(className, "singleObjectArrayToNumber")
        // ```ts
        // return Number([{}]) // NaN
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test singleCustomFortyTwoObjectArrayToNumber`() {
        val method = getMethod(className, "singleCustomFortyTwoObjectArrayToNumber")
        // ```ts
        // return Number([new FortyTwo()]) // 42
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }

    @Disabled("Unsupported sort: Address")
    @Test
    fun `test multipleElementArrayToNumber`() {
        val method = getMethod(className, "multipleElementArrayToNumber")
        // ```ts
        // return Number([5, 100]) // NaN
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }

    @Disabled("Could not resolve unique constructor of anonymous class")
    @Test
    fun `test emptyObjectToNumber`() {
        val method = getMethod(className, "emptyObjectToNumber")
        // ```ts
        // return Number({}) // NaN
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }

    @Disabled("Could not resolve unique constructor of anonymous class")
    @Test
    fun `test objectToNumber`() {
        val method = getMethod(className, "objectToNumber")
        // ```ts
        // return Number({a: 42}) // NaN
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number.isNaN() },
        )
    }

    @Disabled("Could not resolve unique FortyTwo::constructor")
    @Test
    fun `test customFortyTwoObjectToNumber`() {
        val method = getMethod(className, "customFortyTwoObjectToNumber")
        // ```ts
        // return Number(new FortyTwo()) // 42
        // ```
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }
}
