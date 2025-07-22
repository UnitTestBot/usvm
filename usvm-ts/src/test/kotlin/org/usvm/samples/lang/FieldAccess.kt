package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.neq
import kotlin.test.Test

class FieldAccess : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/FieldAccess.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test readDefaultField`() {
        val method = getMethod(className, "readDefaultField")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Test
    fun `test writeAndReadNumeric`() {
        val method = getMethod(className, "writeAndReadNumeric")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 14 },
        )
    }

    @Test
    fun `test writeDifferentTypes`() {
        val method = getMethod(className, "writeDifferentTypes")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Test
    fun `test handleNumericEdges`() {
        val method = getMethod(className, "handleNumericEdges")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
        )
    }

    @Test
    fun `test createWithField`() {
        val method = getMethod(className, "createWithField")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 15 },
        )
    }

    @Disabled("Return types are not propagated to locals, need type stream")
    @Test
    fun `test factoryCreatedObject`() {
        val method = getMethod(className, "factoryCreatedObject")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
        )
    }

    @Test
    fun `test conditionalFieldAccess`() {
        val method = getMethod(className, "conditionalFieldAccess")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { a, r ->
                val x = a.properties["x"] as TsTestValue.TsNumber
                (x eq 1.1) && (r eq 1)
            },
            { a, r ->
                val x = a.properties["x"] as? TsTestValue.TsNumber
                if (x == null) {
                    true
                } else {
                    (x neq 1.1) && (r eq 2)
                }
            },
            invariants = arrayOf(
                { _, r ->
                    r.number in listOf(1, 2).map { it.toDouble() }
                }
            )
        )
    }

    @Disabled("Nested field types are not propagated to locals, need type stream")
    @Test
    fun `test nestedFieldAccess`() {
        val method = getMethod(className, "nestedFieldAccess")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 7 },
        )
    }

    @Disabled("Nested arrays inside objects are accessed via field properties ('.1') instead of indices ([1])")
    @Test
    fun `test arrayFieldAccess`() {
        val method = getMethod(className, "arrayFieldAccess")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5 },
        )
    }

    @Test
    fun `test multipleFieldInteraction`() {
        val method = getMethod(className, "multipleFieldInteraction")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 9 }, // (2*2=4) + (4+1=5) == 9
        )
    }

    @Test
    fun `test circularTypeChanges`() {
        val method = getMethod(className, "circularTypeChanges")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
        )
    }

    @Test
    fun `test read from nested fake objects`() {
        val method = getMethod(className, "readFromNestedFakeObjects")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            { r -> r eq 2 },
        )
    }
}
