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

class TsErrorSourceSemanticsTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/ErrorEtsIr.ts"),
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
    fun `source primitive name and message writes preserve actual runtime kinds`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "overwrittenPrimitiveKinds", className = "ErrorEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `source nullish name and message writes preserve values`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "overwrittenReferenceKinds", className = "ErrorEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `source object message write preserves alias identity`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "overwrittenObjectAlias", className = "ErrorEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `ordinary symbolic string message remains eligible and retains identity`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsBoolean>(
            method = getMethod(methodName = "symbolicMessage", className = "ErrorEtsIr"),
            { message, result -> message.value.isEmpty() && result.value },
            { message, result -> message.value.isNotEmpty() && result.value },
            invariants = arrayOf({ _, result -> result.value }),
        )
    }

    @Test
    fun `throwing an Error through an any field preserves caught identity and payload`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = getMethod(methodName = "catchAnyFieldError", className = "ErrorEtsIr"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `conditional any thrown values preserve both Error and numeric kinds`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "catchConditionalErrorOrNumber", className = "ErrorEtsIr"),
            { flag, result -> flag.value && result.number == 2.0 },
            { flag, result -> !flag.value && result.number == 1.0 },
            invariants = arrayOf({ flag, result -> result.number == if (flag.value) 2.0 else 1.0 }),
        )
    }

    @Test
    fun `source name preserves Error string semantics`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "name", className = "ErrorEtsIr"),
            { result -> result.value == "Error" },
            invariants = arrayOf({ result -> result.value == "Error" }),
        )
    }

    @Test
    fun `source message preserves Error string semantics`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "message", className = "ErrorEtsIr"),
            { result -> result.value == "expected message" },
            invariants = arrayOf({ result -> result.value == "expected message" }),
        )
    }

    @Test
    fun `source overwrittenName preserves Error string semantics`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "overwrittenName", className = "ErrorEtsIr"),
            { result -> result.value == "CustomError" },
            invariants = arrayOf({ result -> result.value == "CustomError" }),
        )
    }

    @Test
    fun `source overwrittenMessage preserves Error string semantics`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "overwrittenMessage", className = "ErrorEtsIr"),
            { result -> result.value == "after" },
            invariants = arrayOf({ result -> result.value == "after" }),
        )
    }

    @Test
    fun `source anyErrorMessage preserves Error string semantics`() {
        discoverProperties<TsTestValue.TsString>(
            method = getMethod(methodName = "anyErrorMessage", className = "ErrorEtsIr"),
            { result -> result.value == "aliased message" },
            invariants = arrayOf({ result -> result.value == "aliased message" }),
        )
    }
}
