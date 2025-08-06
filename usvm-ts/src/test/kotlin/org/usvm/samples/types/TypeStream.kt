package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.neq

class TypeStream : TsMethodTestRunner() {
    private val tsPath = "/samples/types/TypeStream.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test ancestor instanceof`() {
        val method = getMethod("instanceOf")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                (r eq 1) && x.name == "FirstChild"
            },
            { x, r ->
                (r eq 2) && x.name == "SecondChild"
            },
            { x, r ->
                (r eq 3) && x.name == "Parent"
            },
            invariants = arrayOf(
                { x, r ->
                    x.name in listOf("Parent", "FirstChild", "SecondChild")
                },
                { _, r ->
                    r.number in listOf(1.0, 2.0, 3.0)
                },
                { _, r -> r neq -1 }
            )
        )
    }

    @Test
    fun `test virtual invoke on an ancestor`() {
        val method = getMethod("virtualInvokeOnAncestor")
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                (r eq 1) && x.name == "FirstChild"
            },
            { x, r ->
                (r eq 2) && x.name == "SecondChild"
            },
            { x, r ->
                (r eq 3) && x.name == "Parent"
            },
            invariants = arrayOf(
                { x, r ->
                    x.name in listOf("Parent", "FirstChild", "SecondChild")
                },
                { _, r ->
                    r.number in listOf(1.0, 2.0, 3.0)
                },
                { _, r -> r neq -1 }
            )
        )
    }

    @RepeatedTest(10, failureThreshold = 1)
    fun `use unique field`() {
        val method = getMethod("useUniqueField")
        discoverProperties<TsTestValue, TsTestValue>(
            method = method,
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                (r eq 1) && x.name == "FirstChild"
            },
            invariants = arrayOf(
                { x, _ ->
                    if (x is TsTestValue.TsClass) {
                        x.name == "FirstChild"
                    } else true
                },
                { _, r ->
                    if (r is TsTestValue.TsNumber) {
                        r eq 1
                    } else true
                },
            )
        )
    }

    @RepeatedTest(10, failureThreshold = 1)
    fun `use non unique field`() {
        val method = getMethod("useNonUniqueField")
        discoverProperties<TsTestValue, TsTestValue>(
            method = method,
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                (r eq 1) && x.name == "FirstChild"
            },
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                (r eq 2) && x.name == "SecondChild"
            },
            { x, r ->
                x as TsTestValue.TsClass
                r as TsTestValue.TsNumber
                (r eq 3) && x.name == "Parent"
            },
            invariants = arrayOf(
                { _, r ->
                    if (r is TsTestValue.TsNumber) {
                        r.number in listOf(1.0, 2.0, 3.0)
                    } else true
                },
                { _, r ->
                    if (r is TsTestValue.TsNumber) {
                        r neq -1
                    } else true
                },
            )
        )
    }
}
