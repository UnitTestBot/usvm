package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

@Disabled("Statics are not fully supported, yet")
class StaticFields : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test static access get`() {
        val method = getMethod("StaticNumber", "getValue")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 10.0 },
        )
    }

    @Test
    fun `test static default value`() {
        val method = getMethod("StaticDefault", "getValue")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r == TsTestValue.TsUndefined },
        )
    }

    @Test
    fun `test static modification`() {
        val method = getMethod("StaticModification", "incrementTwice")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 },
        )
    }

    @Test
    fun `test static inheritance`() {
        val method = getMethod("StaticDerived", "getId")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 142.0 },
        )
    }

    @Test
    fun `test static inheritance shadowing parent`() {
        val method = getMethod("StaticChild", "getParentId")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 100.0 },
        )
    }

    @Test
    fun `test static inheritance shadowing child`() {
        val method = getMethod("StaticChild", "getChildId")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 200.0 },
        )
    }

    @Test
    fun `test static inheritance shadowing`() {
        val method = getMethod("StaticChild", "getId")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 200.0 },
        )
    }

    @Test
    fun `test static boolean toggle`() {
        val method = getMethod("StaticBoolean", "toggleAndGet")
        discoverProperties<TsTestValue.TsBoolean>(
            method = method,
            { r -> r.value == true },
        )
    }

    @Disabled("Array::push() is not supported")
    @Test
    fun `test static array modification`() {
        val method = getMethod("StaticArray", "pushTwice")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Disabled("Sort mismatch on union type")
    @Test
    fun `test static null initialization`() {
        val method = getMethod("StaticNull", "initialize")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Disabled("Statics are hard... See issue 607 in AA")
    @Test
    fun `test static object manipulation`() {
        val method = getMethod("StaticObject", "modifyAndGet")
        discoverProperties<TsTestValue.TsClass>(
            method = method,
            { r ->
                (r.properties["enabled"] as TsTestValue.TsBoolean).value == true &&
                    (r.properties["count"] as TsTestValue.TsNumber).number == 15.0
            },
        )
    }

    @Test
    fun `test static access sum`() {
        val method = getMethod("StaticAccess", "calculateSum")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 15.0 },
        )
    }

    @Disabled("Array length cannot be properly read from memory due to array type mismatch")
    @Test
    fun `test static access swap`() {
        val method = getMethod("StaticAccess", "swapAndGetValues")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(2.0, 1.0) },
        )
    }

    @Test
    fun `test static any type`() {
        val method = getMethod("StaticAny", "getNumber")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 10.0 },
        )
    }
}
