package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner

class StaticFields : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, useArkAnalyzerTypeInference = true)

    @Test
    fun `test static access get`() {
        val method = getMethod("StaticNumber", "getValue")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 10.0 },
        )
    }

    @Test
    fun `test static modification`() {
        val method = getMethod("StaticModification", "incrementTwice")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 },
        )
    }

    @Test
    fun `test static inheritance parent`() {
        val method = getMethod("StaticChild", "getParentId")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 100.0 },
        )
    }

    @Test
    fun `test static inheritance child`() {
        val method = getMethod("StaticChild", "getChildId")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 200.0 },
        )
    }

    @Test
    fun `test static inheritance this`() {
        val method = getMethod("StaticChild", "getThisId")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 200.0 },
        )
    }

    @Test
    fun `test static boolean toggle`() {
        val method = getMethod("StaticBoolean", "toggleAndGet")
        discoverProperties<TsValue.TsBoolean>(
            method = method,
            { r -> r.value == true },
        )
    }

    @Disabled("Array::push() is not supported")
    @Test
    fun `test static array modification`() {
        val method = getMethod("StaticArray", "pushTwice")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Disabled("Sort mismatch on union type")
    @Test
    fun `test static null initialization`() {
        val method = getMethod("StaticNull", "initialize")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun `test static object manipulation`() {
        val method = getMethod("StaticObject", "modifyAndGet")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r ->
                (r.properties["enabled"] as TsValue.TsBoolean).value == true &&
                    (r.properties["count"] as TsValue.TsNumber).number == 2.0
            },
        )
    }

    @Test
    fun `test static access sum`() {
        val method = getMethod("StaticAccess", "calculateSum")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 3.0 },
        )
    }

    @Disabled("Array length cannot be properly read from memory due to array type mismatch")
    @Test
    fun `test static access swap`() {
        val method = getMethod("StaticAccess", "swapAndGetValues")
        discoverProperties<TsValue.TsArray<TsValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(2.0, 1.0) },
        )
    }
}
