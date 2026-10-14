package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class StaticFieldTypedWrite : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/StaticFieldTypedWrite.ts")

    @Test
    fun `numeric any value is written to typed static field`() {
        val method = getMethod("copy", className = "TypedStaticNumberWrite")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 7 },
            invariants = arrayOf({ result -> result eq 7 }),
        )
    }

    @Test
    fun `boolean any value is written to typed static field`() {
        val method = getMethod("copy", className = "TypedStaticBooleanWrite")

        discoverProperties<TsTestValue.TsBoolean>(
            method = method,
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `conditional source branch keeps compatible numeric static write`() {
        // The frontend lowers the conditional to separate CFG branches.
        val method = getMethod("copyConditionalBranch", className = "TypedStaticNumberWrite")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { useNumber, result -> useNumber.value && (result eq 9) },
            invariants = arrayOf({ useNumber, result -> useNumber.value && (result eq 9) }),
        )
    }
}
