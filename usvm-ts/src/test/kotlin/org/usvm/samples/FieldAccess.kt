package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class FieldAccess : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `test readDefaultField`() {
        val method = getMethod(className, "readDefaultField")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun `test writeAndReadNumeric`() {
        val method = getMethod(className, "writeAndReadNumeric")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 14.0 },
        )
    }

    @Test
    fun `test writeDifferentTypes`() {
        val method = getMethod(className, "writeDifferentTypes")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun `test handleNumericEdges`() {
        val method = getMethod(className, "handleNumericEdges")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
        )
    }

    @Test
    fun `test createWithField`() {
        val method = getMethod(className, "createWithField")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 15.0 },
        )
    }

    @Disabled("Return types are not propagated to locals, need type stream")
    @Test
    fun `test factoryCreatedObject`() {
        val method = getMethod(className, "factoryCreatedObject")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 42.0 },
        )
    }

    @Test
    fun `test conditionalFieldAccess`() {
        val method = getMethod(className, "conditionalFieldAccess")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            { a, r ->
                val x = a.properties["x"] as TsValue.TsNumber
                x.number == 1.1 && r.number == 14.0
            },
            { a, r ->
                val x = a.properties["x"] as TsValue.TsNumber
                x.number != 1.1 && r.number == 10.0
            },
        )
    }

    @Disabled("Nested field types are not propagated to locals, need type stream")
    @Test
    fun `test nestedFieldAccess`() {
        val method = getMethod(className, "nestedFieldAccess")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 7.0 },
        )
    }

    @Disabled("Nested arrays inside objects are accessed via field properties ('.1') instead of indices ([1])")
    @Test
    fun `test arrayFieldAccess`() {
        val method = getMethod(className, "arrayFieldAccess")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 5.0 },
        )
    }

    @Test
    fun `test multipleFieldInteraction`() {
        val method = getMethod(className, "multipleFieldInteraction")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 9.0 }, // (2*2=4) + (4+1=5) == 9
        )
    }

    @Test
    fun `test circularTypeChanges`() {
        val method = getMethod(className, "circularTypeChanges")
        discoverProperties<TsValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
        )
    }
}
