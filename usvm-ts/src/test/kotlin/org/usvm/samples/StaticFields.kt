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
    fun testStaticBasic() {
        val method = getMethod("StaticBasic", "getValue")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 }
        )
    }

    @Test
    fun testStaticModification() {
        val method = getMethod("StaticModification", "incrementAndGet")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 }
        )
    }

    @Test
    fun testStaticInheritance() {
        val parentMethod = getMethod("ParentStatic", "getFamily")
        val childMethod = getMethod("ChildStatic", "getChildFamily")

        discoverProperties<TsValue.TsString>(
            method = parentMethod,
            { r -> r.value == "Parent" }
        )

        discoverProperties<TsValue.TsString>(
            method = childMethod,
            { r -> r.value == "Child" }
        )
    }

    @Test
    fun testStaticShadowing() {
        val method = getMethod("ShadowChild", "getParentId")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 100.0 }
        )
    }

    @Test
    fun testStaticInitializer() {
        val method = getMethod("StaticInitializer", "getComputed")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 15.0 } // Sum of 1-5
        )
    }

    @Test
    fun testMultipleStatics() {
        val method = getMethod("MultipleStatics", "getLogLength")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            // Initial state before any addEntry calls
            { r -> r.number == 0.0 }
        )
    }

    @Test
    fun testStaticObject() {
        val method = getMethod("StaticObject", "checkEnabled")
        discoverProperties<TsValue.TsBoolean>(
            method = method,
            { r -> r.value }
        )
    }

    @Test
    fun testStaticTypes() {
        val method = getMethod("StaticTypes", "getTypeResults")
        discoverProperties<TsValue.TsArray<*>>(
            method = method,
            { r ->
                (r.values[0] as TsValue.TsBoolean).value &&
                    (r.values[1] as TsValue.TsString).value == "Alice" &&
                    (r.values[2] as TsValue.TsNumber).number == 42.0
            }
        )
    }
}
