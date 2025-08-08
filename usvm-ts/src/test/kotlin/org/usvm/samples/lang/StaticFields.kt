package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

@Disabled("Statics are not fully supported, yet")
class StaticFields : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/StaticFields.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test static access get`() {
        val method = getMethod("getValue", className = "StaticNumber")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 10 },
        )
    }

    @Test
    fun `test static default value`() {
        val method = getMethod("getValue", className = "StaticDefault")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r == TsTestValue.TsUndefined },
        )
    }

    @Test
    fun `test static modification`() {
        val method = getMethod("incrementTwice", className = "StaticModification")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 2 },
        )
    }

    @Test
    fun `test static inheritance`() {
        val method = getMethod("getId", className = "StaticDerived")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 142 },
        )
    }

    @Test
    fun `test static inheritance shadowing parent`() {
        val method = getMethod("getParentId", className = "StaticChild")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 100 },
        )
    }

    @Test
    fun `test static inheritance shadowing child`() {
        val method = getMethod("getChildId", className = "StaticChild")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 200 },
        )
    }

    @Test
    fun `test static inheritance shadowing`() {
        val method = getMethod("getId", className = "StaticChild")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 200 },
        )
    }

    @Test
    fun `test static boolean toggle`() {
        val method = getMethod("toggleAndGet", className = "StaticBoolean")
        discoverProperties<TsTestValue.TsBoolean>(
            method = method,
            { r -> r.value },
        )
    }

    @Disabled("Array::push() is not supported")
    @Test
    fun `test static array modification`() {
        val method = getMethod("pushTwice", className = "StaticArray")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Disabled("Sort mismatch on union type")
    @Test
    fun `test static null initialization`() {
        val method = getMethod("initialize", className = "StaticNull")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Disabled("Statics are hard... See issue 607 in AA")
    @Test
    fun `test static object manipulation`() {
        val method = getMethod("modifyAndGet", className = "StaticObject")
        discoverProperties<TsTestValue.TsClass>(
            method = method,
            { r ->
                (r.properties["enabled"] as TsTestValue.TsBoolean).value &&
                    ((r.properties["count"] as TsTestValue.TsNumber) eq 15)
            },
        )
    }

    @Test
    fun `test static access sum`() {
        val method = getMethod("calculateSum", className = "StaticAccess")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 15 },
        )
    }

    @Disabled("Array length cannot be properly read from memory due to array type mismatch")
    @Test
    fun `test static access swap`() {
        val method = getMethod("swapAndGetValues", className = "StaticAccess")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(2.0, 1.0) },
        )
    }

    @Test
    fun `test static any type`() {
        val method = getMethod("getNumber", className = "StaticAny")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 10 },
        )
    }
}
