package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

class CapturedMethodCalls : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene(resourcePath = "/samples/lang/CapturedMethodCalls.ts")

    @Test
    fun `method selection precedes an argument replacing the method`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("methodSelectedBeforeArgument"),
            { result -> result eq 3 },
            invariants = arrayOf({ result -> result eq 3 }),
        )
    }

    @Test
    fun `a local class instance named Math executes its own method`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("shadowedBuiltinClassMethod"),
            { result -> result eq 7 },
            invariants = arrayOf({ result -> result eq 7 }),
        )
    }

    @Test
    fun `callback selection precedes an argument replacing the callback`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("callbackSelectedBeforeArgument"),
            { result -> result eq 4 },
            invariants = arrayOf({ result -> result eq 4 }),
        )
    }
}
