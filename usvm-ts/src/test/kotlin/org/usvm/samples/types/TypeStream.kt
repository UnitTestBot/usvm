package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
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
    fun `reading an undeclared unique field does not narrow the nominal receiver type`() {
        checkDynamicFieldRead(methodName = "useUniqueField")
    }

    @RepeatedTest(10, failureThreshold = 1)
    fun `reading a shared field preserves every compatible nominal receiver type`() {
        checkDynamicFieldRead(methodName = "useNonUniqueField")
    }

    private fun checkDynamicFieldRead(methodName: String) {
        val method = getMethod(methodName)
        val exhaustive = options.copy(stateCollectionStrategy = StateCollectionStrategy.ALL, stopOnCoverage = 0)

        withOptions(exhaustive) {
            discoverProperties<TsTestValue, TsTestValue>(
                method = method,
                { x, r -> x is TsTestValue.TsClass && x.name == "FirstChild" && r is TsTestValue.TsNumber && r eq 1 },
                { x, r -> x is TsTestValue.TsClass && x.name == "SecondChild" && r is TsTestValue.TsNumber && r eq 2 },
                { x, r -> x is TsTestValue.TsClass && x.name == "Parent" && r is TsTestValue.TsNumber && r eq 3 },
                { x, r -> x == TsTestValue.TsUndefined && r is TsTestValue.TsException },
                invariants = arrayOf({ x, r ->
                    if (r is TsTestValue.TsNumber) {
                        x is TsTestValue.TsClass && when (x.name) {
                            "FirstChild" -> r eq 1
                            "SecondChild" -> r eq 2
                            "Parent" -> r eq 3
                            else -> false
                        }
                    } else {
                        (x == TsTestValue.TsUndefined || x == TsTestValue.TsNull) && r is TsTestValue.TsException
                    }
                }),
            )
        }
    }
}
