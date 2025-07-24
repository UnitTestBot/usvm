package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class Async : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Async.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `await resolving promise`() {
        val method = getMethod("awaitResolvingPromise")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `await rejecting promise`() {
        val method = getMethod("awaitRejectingPromise")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `await resolved promise`() {
        val method = getMethod("awaitResolvedPromise")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 }
            )
        )
    }

    @Test
    fun `await rejected promise`() {
        val method = getMethod("awaitRejectedPromise")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }
}
