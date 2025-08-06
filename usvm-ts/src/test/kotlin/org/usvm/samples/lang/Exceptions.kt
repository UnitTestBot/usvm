package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class Exceptions : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/Exceptions.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `simple throw`() {
        val method = getMethod("simpleThrow")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `throw string`() {
        val method = getMethod("throwString")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `throw number`() {
        val method = getMethod("throwNumber")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `throw boolean`() {
        val method = getMethod("throwBoolean")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `throw null`() {
        val method = getMethod("throwNull")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `throw undefined`() {
        val method = getMethod("throwUndefined")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `conditional throw`() {
        val method = getMethod("conditionalThrow")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { shouldThrow, r ->
                // throw path
                shouldThrow.value && r is TsTestValue.TsException
            },
            { shouldThrow, r ->
                // normal path
                !shouldThrow.value && r is TsTestValue.TsNumber && (r eq 42)
            },
        )
    }
}
