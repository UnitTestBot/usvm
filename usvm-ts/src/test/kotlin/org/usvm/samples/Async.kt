package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class Async : TsMethodTestRunner() {

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(className)

    @Test
    fun `await resolving promise`() {
        val method = getMethod(className, "awaitResolvingPromise")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number eq 42 },
            )
        )
    }

    @Test
    fun `await rejecting promise`() {
        val method = getMethod(className, "awaitRejectingPromise")
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
                { r -> r.number eq 42 },
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
