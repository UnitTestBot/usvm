package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class Async2 : TsMethodTestRunner() {

    companion object {
        private const val SDK_TYPESCRIPT_PATH = "/sdk/typescript"
        private const val SDK_OHOS_PATH = "/sdk/ohos"
    }

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(
        className,
        sdks = listOf(SDK_TYPESCRIPT_PATH, SDK_OHOS_PATH)
    )

    @Test
    fun `chain promises`() {
        val method = getMethod(className, "chainPromises")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 20.0 },
            )
        )
    }

    @Test
    fun `chain with rejection`() {
        val method = getMethod(className, "chainWithRejection")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 42.0 },
            )
        )
    }

    @Test
    fun `await async function`() {
        val method = getMethod(className, "awaitAsyncFunction")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 10.0 },
            )
        )
    }

    @Test
    fun `multiple awaits`() {
        val method = getMethod(className, "multipleAwaits")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 8.0 },
            )
        )
    }

    @Test
    fun `promise all`() {
        val method = getMethod(className, "promiseAll")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 6.0 },
            )
        )
    }

    @Test
    fun `promise race`() {
        val method = getMethod(className, "promiseRace")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 42.0 },
            )
        )
    }

    @Disabled("Long running failing test")
    @Test
    fun `nested async`() {
        val method = getMethod(className, "nestedAsync")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 20.0 },
            )
        )
    }

    @Test
    fun `conditional await true`() {
        val method = getMethod(className, "conditionalAwait")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 100.0 },
            invariants = arrayOf(
                { r -> r.number == 100.0 || r.number == 200.0 },
            )
        )
    }

    @Test
    fun `async try catch`() {
        val method = getMethod(className, "asyncTryCatch")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 50.0 },
            )
        )
    }

    @Test
    fun `async finally`() {
        val method = getMethod(className, "asyncFinally")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 15.0 },
            )
        )
    }

    @Test
    fun `concurrent operations`() {
        val method = getMethod(className, "concurrentOperations")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 35.0 },
            )
        )
    }

    @Test
    fun `immediate resolve`() {
        val method = getMethod(className, "immediateResolve")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 123.0 },
            )
        )
    }

    @Test
    fun `immediate reject`() {
        val method = getMethod(className, "immediateReject")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
        )
    }

    @Test
    fun `conditional promise resolve`() {
        val method = getMethod(className, "conditionalPromise")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 777.0 },
            invariants = arrayOf(
                { r -> r.number == 777.0 },
            )
        )
    }

    @Test
    fun `conditional promise reject`() {
        val method = getMethod(className, "conditionalPromise")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
        )
    }

    @Test
    fun `await primitives`() {
        val method = getMethod(className, "awaitPrimitives")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 42.0 },
            )
        )
    }

    @Test
    fun `mixed values`() {
        val method = getMethod(className, "mixedValues")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 60.0 },
            )
        )
    }

    @Test
    fun `recursive async`() {
        val method = getMethod(className, "recursiveAsync")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number >= 0.0 }, // Sum of 1+2+...+n should be positive
            invariants = arrayOf(
                { r -> r.number >= 0.0 },
            )
        )
    }

    @Test
    fun `nested promise resolution`() {
        val method = getMethod(className, "nestedPromiseResolution")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 88.0 },
            )
        )
    }

    @Test
    fun `error propagation`() {
        val method = getMethod(className, "errorPropagation")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            invariants = arrayOf(
                { r -> r.number == 99.0 },
            )
        )
    }
}
