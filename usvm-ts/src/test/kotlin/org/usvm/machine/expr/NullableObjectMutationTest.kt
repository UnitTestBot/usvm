package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TS_TYPE_ERROR_TYPE
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NullableObjectMutationTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    override val scene = loadScene("/models/NullableObjectMutation.ts")
    override val tsOptions = TsOptions(
        unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()),
        unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
    )
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyze(methods = listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `null and undefined assign targets follow the JavaScript exception path`() {
        for (methodName in listOf("assignNull", "assignUndefined")) {
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(methodName = methodName, className = "NullableObjectMutation"),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `uncaught nullish target produces the interpreter TypeError identity`() {
        val method = getMethod(methodName = "uncaughtAssignUndefined", className = "NullableObjectMutation")
        val states = TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
            machine.analyze(methods = listOf(method))
        }

        val exception = assertIs<TsMethodResult.TsException>(states.single().methodResult)
        assertEquals(TS_TYPE_ERROR_TYPE, exception.type)
    }

    @Test
    fun `nullable target fork preserves both caught exception and normal object sibling`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "nullableChoice", className = "NullableObjectMutation"),
            { flag, result -> flag.value && result.number == 1.0 },
            { flag, result -> !flag.value && result.number == 2.0 },
            invariants = arrayOf({ flag, result -> result.number == if (flag.value) 1.0 else 2.0 }),
        )
    }

    @Test
    fun `nullish targets of noncoercing Object operations reach normal dispatch`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "freezeNullish", className = "NullableObjectMutation"),
            { result -> result.number == 11.0 },
            invariants = arrayOf({ result -> result.number == 11.0 }),
        )
    }

    @Test
    fun `user receiver named assign retains its own null handling`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "userReceiver", className = "NullableObjectMutation"),
            { result -> result.number == 19.0 },
            invariants = arrayOf({ result -> result.number == 19.0 }),
        )
    }

    @Test
    fun `undefined prototype property access follows the normal exception path`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "undefinedPrototype", className = "NullableObjectMutation"),
            { result -> result.number == 13.0 },
            invariants = arrayOf({ result -> result.number == 13.0 }),
        )
    }

    @Test
    fun `native JavaScript confirms nullable operation and user receiver results`() {
        val source = getResourcePath("/models/NullableObjectMutation.ts").readText()
        val checks = """
            const checks = [
                [NullableObjectMutation.assignNull(), 1],
                [NullableObjectMutation.assignUndefined(), 1],
                [NullableObjectMutation.nullableChoice(true), 1],
                [NullableObjectMutation.nullableChoice(false), 2],
                [NullableObjectMutation.freezeNullish(), 11],
                [NullableObjectMutation.userReceiver(), 19],
                [NullableObjectMutation.undefinedPrototype(), 13],
                [NullableObjectMutation.arrayChoice(false), 7],
            ];
            try {
                NullableObjectMutation.uncaughtAssignUndefined();
                throw Error("Expected Object.assign to reject an undefined target");
            } catch (error) {
                if (!(error instanceof TypeError)) throw error;
            }
            for (const [actual, expected] of checks) {
                if (actual !== expected) throw Error(`Expected ${'$'}{expected}, got ${'$'}{actual}`);
            }
        """.trimIndent()

        assertNodeReplay(
            source = source + checks,
            directory = directory,
            name = "nullable-object-mutation",
            timeoutMessage = "Nullable Object operation replay timed out",
        )
    }

    @Test
    fun `reflective array branch remains unsupported while object sibling is retained`() {
        val method = getMethod(methodName = "arrayChoice", className = "NullableObjectMutation")
        val outcome = TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }
        val tests = outcome.states.map { state -> TsTestResolver().resolve(method, state) }

        assertEquals(1, outcome.unsupportedPaths.size)
        assertTrue(outcome.unsupportedPaths.single().contains("Reflective Object operations on arrays"))
        assertTrue(tests.isNotEmpty())
        assertTrue(tests.all { test -> test.before.parameters == listOf(TsTestValue.TsBoolean(value = false)) })
        assertTrue(tests.all { test -> (test.returnValue as TsTestValue.TsNumber).number == 7.0 })
    }
}
