package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class Async : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `create and await promise`() {
        val method = getMethod(className, "createAndAwaitPromise")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 },
            )
        )
    }

    @Test
    fun `create and await rejecting promise`() {
        val method = getMethod(className, "createAndAwaitRejectingPromise")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
        )
    }

    @Test
    fun `await resolved promise`() {
        val method = getMethod(className, "awaitResolvedPromise")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 50.0 },
            )
        )
    }

    @Test
    fun `await rejected promise`() {
        val method = getMethod(className, "awaitRejectedPromise")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
        )
    }
}
