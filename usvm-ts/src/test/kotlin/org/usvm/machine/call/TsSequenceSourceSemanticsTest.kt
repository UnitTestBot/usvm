package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath

class TsSequenceSourceSemanticsTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/SequenceEtsIr.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    @Test
    fun `modeled strings compare by UTF-16 value`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "stringModelsUseValueEquality", className = "SequenceEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `non-string references retain identity equality`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "stringEqualityPreservesReferenceIdentity", className = "SequenceEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `fake reference strings compare by UTF-16 value`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "stringValueEqualityThroughAny", className = "SequenceEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `string truthiness follows UTF-16 length`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "stringTruthinessUsesLength", className = "SequenceEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `non-string truthiness does not read string storage`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "nonStringTruthinessDoesNotReadStringStorage", className = "SequenceEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `mixed object equality does not read string storage`() {
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean>(
            method = getMethod(methodName = "mixedObjectEqualityDoesNotReadStringStorage", className = "SequenceEtsIr"),
            { _, result -> result.value },
            invariants = arrayOf({ _, result -> result.value }),
        )
    }

    @Test
    fun `string slice copies exact UTF16 code units`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "stringSlicePreservesUtf16", className = "SequenceEtsIr"),
            { result -> result.value == "😀" },
            invariants = arrayOf({ result -> result.value == "😀" }),
        )
    }

    @Test
    fun `ASCII casing composes with charAt slice and concatenation`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "stringCapitalizeAscii", className = "SequenceEtsIr"),
            { result -> result.value == "Hello" },
            invariants = arrayOf({ result -> result.value == "Hello" }),
        )
    }

    @Test
    fun `empty string capitalization remains empty`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "stringCapitalizeEmpty", className = "SequenceEtsIr"),
            { result -> result.value == "" },
            invariants = arrayOf({ result -> result.value == "" }),
        )
    }
}
