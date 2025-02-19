package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class StaticFields : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = run {
        val name = "$className.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testStaticNumber() {
        val method = getMethod("StaticNumber", "getValue")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 10.0 },
        )
    }

    @Test
    fun testStaticModification() {
        val method = getMethod("StaticModification", "incrementTwice")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 2.0 },
        )
    }

    @Test
    fun testStaticInheritanceParent() {
        val method = getMethod("StaticChild", "getParentId")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 100.0 },
        )
    }

    @Test
    fun testStaticInheritanceChild() {
        val method = getMethod("StaticChild", "getChildId")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 200.0 },
        )
    }

    @Test
    fun testStaticBooleanToggle() {
        val method = getMethod("StaticBoolean", "toggleAndGet")
        discoverProperties<TsValue.TsBoolean>(
            method = method,
            { r -> r.value == false },
        )
    }

    @Test
    fun testStaticArrayOperations() {
        val method = getMethod("StaticArray", "pushTwice")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun testStaticNullInitialization() {
        val method = getMethod("StaticNull", "initialize")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun testStaticObjectOperations() {
        val method = getMethod("StaticObject", "toggleAndGet")
        discoverProperties<TsValue.TsClass>(
            method = method,
            { r ->
                (r.properties["enabled"] as TsValue.TsBoolean).value == true &&
                    (r.properties["count"] as TsValue.TsNumber).number == 1.0
            },
            { r ->
                (r.properties["enabled"] as TsValue.TsBoolean).value == false &&
                    (r.properties["count"] as TsValue.TsNumber).number == 2.0
            },
        )
    }

    @Test
    fun testStaticAccess() {
        val method = getMethod("StaticAccess", "calculateSum")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 3.0 },
        )
    }

    @Test
    fun testStaticAccessSwap() {
        val method = getMethod("StaticAccess", "swapAndGetValues")
        discoverProperties<TsValue.TsArray<TsValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(2.0, 1.0) },
        )
    }
}
