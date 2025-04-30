package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner

class TypeStream : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "types")

    @Test
    fun `test an ancestor as argument`() {
        val method = getMethod(className, "ancestorId")
        discoverProperties<TsValue.TsClass, TsValue.TsClass>(
            method = method,
            { value, r -> r.name == value.name },
        )
    }

    @Test
    fun `test virtual invoke on an ancestor`() {
        val method = getMethod(className, "virtualInvokeForAncestor")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            { value, r -> value.name == "Parent" && r.number == 1.0 },
            { value, r -> value.name == "FirstChild" && r.number == 2.0 },
            { value, r -> value.name == "SecondChild" && r.number == 3.0 },
        )
    }

    @Test
    fun `use unique field`() {
        val method = getMethod(className, "useUniqueField")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { value, r -> value.name == "FirstChild" && r.number == 1.0 }
            )
        )
    }

    @Test
    fun `use non unique field`() {
        val method = getMethod(className, "useNonUniqueField")
        discoverProperties<TsValue.TsClass, TsValue.TsNumber>(
            method = method,
            { value, r -> value.name == "Parent" && r.number == 1.0 },
            { value, r -> value.name == "FirstChild" && r.number == 2.0 },
            { value, r -> value.name == "SecondChild" && r.number == 3.0 },
        )
    }
}