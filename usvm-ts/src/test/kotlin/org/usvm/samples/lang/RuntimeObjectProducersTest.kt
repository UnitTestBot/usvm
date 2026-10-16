package org.usvm.samples.lang

import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner

class RuntimeObjectProducersTest : TsMethodTestRunner() {
    override val scene = loadScene(resourcePath = "/samples/lang/RuntimeObjectProducers.ts")

    init {
        options = options.copy(stateCollectionStrategy = StateCollectionStrategy.ALL)
    }

    @Test
    fun `source module receiver has an object runtime type`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "moduleReceiverIsTruthy", className = "RuntimeObjectProducers"),
            { result -> result.number == 17.0 },
            invariants = arrayOf({ result -> result.number == 17.0 }),
        )
    }

    @Test
    fun `negating an async result preserves both settlement branches`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "fulfilledPromiseNegation", className = "RuntimeObjectProducers"),
            { selectZero, result -> selectZero.value && result.number == 101.0 },
            { selectZero, result -> !selectZero.value && result.number == 106.0 },
            invariants = arrayOf({ selectZero, result ->
                result.number == if (selectZero.value) 101.0 else 106.0
            }),
        )
    }

    @Test
    fun `async results remain truthy after returning zero or a nonzero number`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "fulfilledPromiseCondition", className = "RuntimeObjectProducers"),
            { selectZero, result -> selectZero.value && result.number == 201.0 },
            { selectZero, result -> !selectZero.value && result.number == 206.0 },
            invariants = arrayOf({ selectZero, result ->
                result.number == if (selectZero.value) 201.0 else 206.0
            }),
        )
    }

    @Test
    fun `conditional promise and null retain distinct truthiness`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "conditionalPromiseOrNull", className = "RuntimeObjectProducers"),
            { usePromise, result -> usePromise.value && result.number == 301.0 },
            { usePromise, result -> !usePromise.value && result.number == 311.0 },
            invariants = arrayOf({ usePromise, result ->
                result.number == if (usePromise.value) 301.0 else 311.0
            }),
        )
    }

    @Test
    fun `conditional promise and undefined retain distinct truthiness`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "conditionalPromiseOrUndefined", className = "RuntimeObjectProducers"),
            { usePromise, result -> usePromise.value && result.number == 501.0 },
            { usePromise, result -> !usePromise.value && result.number == 511.0 },
            invariants = arrayOf({ usePromise, result ->
                result.number == if (usePromise.value) 501.0 else 511.0
            }),
        )
    }

    @Test
    fun `rejected async result remains an object before awaiting it`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "rejectedPromiseCondition", className = "RuntimeObjectProducers"),
            { result -> result.number == 401.0 },
            invariants = arrayOf({ result -> result.number == 401.0 }),
        )
    }
}
