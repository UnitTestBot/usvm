package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class Exceptions : TsMethodTestRunner() {
    private val tsPath = "/samples/Exceptions.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `simple throw`() {
        val method = getMethod(className, "simpleThrow")
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
        val method = getMethod(className, "throwString")
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
        val method = getMethod(className, "throwNumber")
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
        val method = getMethod(className, "throwBoolean")
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
        val method = getMethod(className, "throwNull")
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
        val method = getMethod(className, "throwUndefined")
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
        val method = getMethod(className, "conditionalThrow")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { shouldThrow, r ->
                // throw path
                shouldThrow.value && r is TsTestValue.TsException
            },
            { shouldThrow, r ->
                // normal path
                !shouldThrow.value && r is TsTestValue.TsNumber && r.number == 42.0
            },
        )
    }
}
