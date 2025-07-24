package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

@Disabled("delete operator is not supported")
class DeleteOperator : TsMethodTestRunner() {
    private val tsPath = "/samples/operators/DeleteOperator.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `delete property`() {
        val method = getMethod("testDeleteProperty")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `delete non-existent property`() {
        val method = getMethod("testDeleteNonExistentProperty")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `delete local variable`() {
        val method = getMethod("testDeleteLocalVariable")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `delete non-existent variable`() {
        val method = getMethod("testDeleteNonExistentVariable")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }
}
