package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.Test

class ObjectLiteralRuntimeFields : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/ObjectLiteralRuntimeFields.ts")

    init {
        options = options.copy(
            stopOnCoverage = 0,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `forwarded optional Boolean preserves undefined false and true`() {
        val method = getMethod("forwardOptionalBoolean")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { present, _, result -> !present.value && (result eq -1) },
            { present, include, result -> present.value && !include.value && (result eq 0) },
            { present, include, result -> present.value && include.value && (result eq 1) },
            invariants = arrayOf({ present, include, result ->
                val expected = if (!present.value) -1 else if (include.value) 1 else 0
                result eq expected
            }),
        )
    }

    @Test
    fun `runtime storage follows each fork of the same literal`() {
        val method = getMethod("branchWrites")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { change, result -> change.value && (result eq 1) },
            { change, result -> !change.value && (result eq 2) },
            invariants = arrayOf({ change, result -> result eq if (change.value) 1 else 2 }),
        )
    }

    @Test
    fun `declared literal fields observe each later runtime kind`() {
        val method = getMethod("declaredLiteralFieldsKeepRuntimeKinds")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }
}
