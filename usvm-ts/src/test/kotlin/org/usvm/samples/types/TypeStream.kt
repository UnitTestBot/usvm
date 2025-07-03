package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class TypeStream : TsMethodTestRunner() {
    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className, folderPrefix = "types")

    @Test
    fun `test ancestor instanceof`() {
        val method = getMethod(className, "instanceOf")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                x.name == "FirstChild" && r.number == 1.0
            },
            { x, r ->
                x.name == "SecondChild" && r.number == 2.0
            },
            { x, r ->
                x.name == "Parent" && r.number == 3.0
            },
            invariants = arrayOf(
                { x, r ->
                    x.name in listOf("Parent", "FirstChild", "SecondChild")
                },
                { _, r ->
                    r.number in listOf(1.0, 2.0, 3.0)
                },
                { _, r -> r.number != -1.0 }
            )
        )
    }

    @Test
    fun `test virtual invoke on an ancestor`() {
        val method = getMethod(className, "virtualInvokeOnAncestor")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                x.name == "FirstChild" && r.number == 1.0
            },
            { x, r ->
                x.name == "SecondChild" && r.number == 2.0
            },
            { x, r ->
                x.name == "Parent" && r.number == 3.0
            },
            invariants = arrayOf(
                { x, r ->
                    x.name in listOf("Parent", "FirstChild", "SecondChild")
                },
                { _, r ->
                    r.number in listOf(1.0, 2.0, 3.0)
                },
                { _, r -> r.number != -1.0 }
            )
        )
    }

    @RepeatedTest(10, failureThreshold = 1)
    fun `use unique field`() {
        val method = getMethod(className, "useUniqueField")
        discoverProperties<TsTestValue, TsTestValue>(
            method = method,
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                x.name == "FirstChild" && r.number == 1.0
            },
            invariants = arrayOf(
                { x, _ ->
                    x !is TsTestValue.TsClass || x.name == "FirstChild"
                },
                { _, r ->
                    r !is TsTestValue.TsNumber || r.number == 1.0
                },
            )
        )
    }

    @RepeatedTest(10, failureThreshold = 1)
    fun `use non unique field`() {
        val method = getMethod(className, "useNonUniqueField")
        discoverProperties<TsTestValue, TsTestValue>(
            method = method,
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                x.name == "FirstChild" && r.number == 1.0
            },
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                x.name == "SecondChild" && r.number == 2.0
            },
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                x.name == "Parent" && r.number == 3.0
            },
            invariants = arrayOf(
                { _, r ->
                    r !is TsTestValue.TsNumber || r.number in listOf(1.0, 2.0, 3.0)
                },
                { _, r ->
                    r !is TsTestValue.TsNumber || r.number != -1.0
                },
            )
        )
    }
}
