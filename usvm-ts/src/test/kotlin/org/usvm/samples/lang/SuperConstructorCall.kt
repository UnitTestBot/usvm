package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class SuperConstructorCall : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/SuperConstructorCall.ts")

    @Test
    fun `implicit derived constructor calls the parent once`() {
        val method = getMethod("implicitSuper")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 7 },
            invariants = arrayOf({ result -> result eq 7 }),
        )
    }

    @Test
    fun `explicit super preserves arguments and derived receiver`() {
        val method = getMethod("explicitSuper")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 8 },
            invariants = arrayOf({ result -> result eq 8 }),
        )
    }

    @Test
    fun `ordinary constructor property remains callable`() {
        val method = getMethod("shadowedConstructorProperty")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 17 },
            invariants = arrayOf({ result -> result eq 17 }),
        )
    }
}
