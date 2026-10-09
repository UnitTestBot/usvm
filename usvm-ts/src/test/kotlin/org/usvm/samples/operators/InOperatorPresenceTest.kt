package org.usvm.samples.operators

import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.util.eq
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InOperatorPresenceTest : PropertyTestRunner("/samples/operators/InOperator.ts") {
    @Test
    fun `in checks own field presence through writes and deletion`() {
        val methods = listOf(
            "hasPresentNumberProperty",
            "hasUndefinedProperty",
            "lacksProperty",
            "lacksOptionalProperty",
            "hasOwnConstructorMethod",
            "hasAddedProperty",
            "lacksDeletedProperty",
            "hasRestoredProperty",
        )

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "InOperator")

            discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
                method = method,
                { _, result -> result eq 1 },
                invariants = arrayOf({ _, result -> result eq 1 }),
            )
        }
    }

    @Test
    fun `conditional deletion preserves both presence outcomes`() {
        val method = getMethod(methodName = "conditionalDelete", className = "InOperator")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldDelete, result -> shouldDelete.value && (result eq 0) },
            { shouldDelete, result -> !shouldDelete.value && (result eq 1) },
            invariants = arrayOf({ shouldDelete, result -> result eq (if (shouldDelete.value) 0 else 1) }),
        )
    }

    @Test
    fun `added field remains readable after presence check`() {
        val methodName = "readsAddedProperty"
        val method = getMethod(methodName = methodName, className = "InOperator")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { input, result -> result eq input },
            invariants = arrayOf({ input, result -> result eq input }),
        )
    }

    @Test
    fun `absent optional field reads as undefined after negative presence check`() {
        val methodName = "readsMissingOptionalAfterIn"
        val method = getMethod(methodName = methodName, className = "InOperator")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `block scoped top level write updates field presence`() {
        val methodName = "readsBlockScopedResult"
        val method = getMethod(methodName = methodName, className = "InOperator")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 7 },
            invariants = arrayOf({ result -> result eq 7 }),
        )
    }

    @Test
    fun `unmodeled keys arrays and prototypes have explicit unsupported outcomes`() {
        val methods = listOf(
            "hasSymbolicKey",
            "testInOperatorObject",
            "testInOperatorArray",
            "testInOperatorObjectAfterDelete",
            "specialPrototypeInitializer",
            "inheritedThroughPrototypeInitializer",
            "inheritedThroughAssignedPrototype",
            "deletedToStringExposesPrototype",
            "inheritedConstructor",
        )

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "InOperator")
            val outcome = TsMachine(
                scene = scene,
                options = options.copy(throwExceptionOnStepFailure = false),
                tsOptions = tsOptions,
            ).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isEmpty(), methodName)
            assertTrue(outcome.unsupportedPaths.isNotEmpty(), methodName)
            if (methodName.endsWith("PrototypeInitializer") || methodName == "inheritedThroughAssignedPrototype") {
                assertTrue(outcome.unsupportedPaths.any { "prototype mutation" in it },
                    "${outcome.unsupportedPaths}")
            }
            if (methodName == "deletedToStringExposesPrototype" || methodName == "inheritedConstructor") {
                assertTrue(outcome.unsupportedPaths.any { it.contains("prototype lookup", ignoreCase = true) },
                    "${outcome.unsupportedPaths}")
            }
        }

        replayPrototypeInitializer()
    }

    private fun replayPrototypeInitializer() {
        val assertions =
            "if (new InOperator().specialPrototypeInitializer() !== false) throw Error('null prototype');\n" +
            "if (new InOperator().inheritedThroughPrototypeInitializer() !== true) throw Error('inherited');\n" +
            "if (new InOperator().inheritedThroughAssignedPrototype() !== true) throw Error('assigned prototype');\n" +
            "if (new InOperator().deletedToStringExposesPrototype() !== true) throw Error('revealed prototype');\n" +
            "if (new InOperator().inheritedConstructor() !== true) throw Error('inherited constructor');\n"

        replayInOperatorScript(directory, sourcePath, scriptName = "specialPrototypeInitializer", assertions = assertions)
    }
}
