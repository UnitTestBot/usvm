package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.test.util.checkers.noResultsExpected
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

open class TsTypeAssertionOptionsTestBase : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/TypeAssertionOptions.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    init {
        options = options.copy(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }
}

class TsTypeAssertionDefaultTest : TsTypeAssertionOptionsTestBase() {
    @Test
    fun `default casts reject an incompatible allocated reference type`() {
        checkMatches<TsTestValue.TsNumber>(
            method = getMethod(methodName = "incompatibleReference", className = "TypeAssertionOptions"),
            analysisResultNumberMatches = noResultsExpected,
        )
    }
}

class TsTypeAssertionErasureTest : TsTypeAssertionOptionsTestBase() {
    override val tsOptions = TsOptions(eraseTypeAssertions = true)

    @Test
    fun `erased assertions preserve the original reference despite incompatible annotation`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "incompatibleReference", className = "TypeAssertionOptions"),
            { result -> result.number == 7.0 },
            invariants = arrayOf({ result -> result.number == 7.0 }),
        )
    }

    @Test
    fun `erased as assertions preserve numeric runtime values`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "numericAssertion", className = "TypeAssertionOptions"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `erased angle bracket assertions preserve boolean runtime values`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "booleanAssertion", className = "TypeAssertionOptions"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }
}
