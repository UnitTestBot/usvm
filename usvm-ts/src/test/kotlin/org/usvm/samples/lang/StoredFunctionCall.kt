package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class StoredFunctionCall : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/StoredFunctionCall.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test callback stored in constructor`() {
        val method = getMethod("constructorCallback")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 2 },
            invariants = arrayOf({ result -> result eq 2 }),
        )
    }

    @Test
    fun `test regular function uses call receiver`() {
        val method = getMethod("regularFunction")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 5 },
            invariants = arrayOf({ result -> result eq 5 }),
        )
    }
}
