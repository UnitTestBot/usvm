package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

@Disabled("`in` operator is not supported yet")
class InOperator : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/InOperator.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `in operator with object`() {
        val method = getMethod("testInOperatorObject")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `in operator with object after delete`() {
        val method = getMethod("testInOperatorObjectAfterDelete")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `in operator with array`() {
        val method = getMethod("testInOperatorArray")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `in operator with array after delete`() {
        val method = getMethod("testInOperatorArrayAfterDelete")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `in operator with string`() {
        val method = getMethod("testInOperatorString")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }
}
