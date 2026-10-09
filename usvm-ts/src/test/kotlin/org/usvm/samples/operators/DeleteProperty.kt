package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class DeleteProperty : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    override val scene: EtsScene = loadScene("/samples/operators/DeleteProperty.ts")

    @Test
    fun `number property reads as undefined after delete`() {
        val method = getMethod("readAfterDelete")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsUndefined>(
            method = method,
            { _, result -> result == TsTestValue.TsUndefined },
            invariants = arrayOf({ _, result -> result == TsTestValue.TsUndefined }),
        )
    }

    @Test
    fun `delete example exhausts paths without interpreter failure`() {
        val method = getMethod("readAfterDelete")
        val options = UMachineOptions(
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            throwExceptionOnStepFailure = true,
        )

        val result = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason)
        assertTrue(result.states.isNotEmpty())
    }

    @Test
    fun `boolean property reads as undefined after delete`() {
        val method = getMethod("deleteBoolean")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `typed number property reads as undefined after delete`() {
        val method = getMethod("deleteTypedNumber")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `reference property reads as undefined after delete`() {
        val method = getMethod("deleteReference")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `alias observes delete`() {
        val method = getMethod("aliasSeesDelete")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `reassignment restores deleted property`() {
        val method = getMethod("restoreAfterDelete")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `deleting missing property succeeds`() {
        val method = getMethod("deleteMissing")

        discoverProperties<TsTestValue.TsNumber>(
            method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `conditional delete preserves both read outcomes`() {
        val method = getMethod("readAfterConditionalDelete")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldDelete, result -> shouldDelete.value && (result eq 1) },
            { shouldDelete, result -> !shouldDelete.value && (result eq 2) },
            invariants = arrayOf({ shouldDelete, result -> result eq (if (shouldDelete.value) 1 else 2) }),
        )

        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val analysis = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }
        val tests = analysis.states.map { state -> TsTestResolver().resolve(method, state) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.unsupportedPaths.isEmpty(), "${analysis.unsupportedPaths}")
        assertEquals(setOf(false, true), tests.map { test ->
            assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
        }.toSet())
        tests.forEach { test ->
            val shouldDelete = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number.toInt()
            assertEquals(if (shouldDelete) 1 else 2, result, "$test")
        }
    }

    @Test
    fun `typed string property can be deleted and restored`() {
        val method = getMethod("deleteAndRestoreString")

        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { _, result -> result eq 1 },
            invariants = arrayOf({ _, result -> result eq 1 }),
        )
    }

    @Test
    fun `conditional string delete preserves the untouched value`() {
        val method = getMethod("conditionalDeleteString")

        discoverProperties<TsTestValue.TsString, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { _, shouldDelete, result -> shouldDelete.value && (result eq 1) },
            { _, shouldDelete, result -> !shouldDelete.value && (result eq 2) },
            invariants = arrayOf({ _, shouldDelete, result -> result eq (if (shouldDelete.value) 1 else 2) }),
        )
    }

    @Test
    fun `deleting a property of an input object reads as undefined`() {
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = getMethod("deleteInput"),
            { _, result -> result eq 1 },
            invariants = arrayOf({ _, result -> result eq 1 }),
        )
    }

    @Test
    fun `input property is not marked deleted before any delete`() {
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = getMethod("untouchedInput"),
            { _, result -> result eq 1 },
            invariants = arrayOf({ _, result -> result eq 1 }),
        )
    }

    @Test
    fun `input alias observes delete and reassignment restores the field`() {
        for (name in listOf("deleteInputAlias", "restoreInput")) {
            discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
                method = getMethod(name),
                { _, result -> result eq 1 },
                invariants = arrayOf({ _, result -> result eq 1 }),
            )
        }
    }

    @Test
    fun `conditional input deletion preserves both paths`() {
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod("conditionalDeleteInput"),
            { _, shouldDelete, result -> shouldDelete.value && (result eq 1) },
            { _, shouldDelete, result -> !shouldDelete.value && (result eq 2) },
            invariants = arrayOf({ _, shouldDelete, result -> result eq (if (shouldDelete.value) 1 else 2) }),
        )
    }

    @Test
    fun `delete affects aliased inputs and preserves a distinct receiver`() {
        discoverProperties<TsTestValue.TsClass, TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = getMethod("deletePossiblyAliasedInputs"),
            { _, _, result -> result eq 1 },
            { _, _, result -> result eq 2 },
            invariants = arrayOf({ _, _, result -> (result eq 1) || (result eq 2) }),
        )
    }

    @Test
    fun `deleting a value expression returns true`() {
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean>(
            method = getMethod("deleteValueExpression"),
            { _, result -> result.value },
            invariants = arrayOf({ _, result -> result.value }),
        )
    }

    @Test
    fun `deleting an assignment expression preserves its side effect`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("deleteAssignmentExpression"),
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `type assertion on a value preserves delete semantics`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("deleteCastedValue"),
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }

    @Test
    fun `type assertion losing a property reference reports unsupported`() {
        val method = getMethod("deleteCastedField")
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val analysis = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.states.isEmpty())
        assertTrue(analysis.unsupportedPaths.any { "Deleting EtsLocal" in it })
    }

    @Test
    fun `input deletion and value operand witnesses replay in Node`() {
        val names = listOf(
            "deleteInput", "untouchedInput", "deleteInputAlias", "restoreInput", "conditionalDeleteInput",
            "deleteValueExpression", "deleteAssignmentExpression", "deleteCastedValue",
        )
        val options = UMachineOptions(stateCollectionStrategy = StateCollectionStrategy.ALL, stopOnCoverage = 0)
        val tests = names.associateWith { name -> runner(getMethod(name), options) }

        fun serialize(value: TsTestValue): String = when (value) {
            is TsTestValue.TsClass -> value.properties.entries.joinToString(prefix = "{", postfix = "}") { (name, field) ->
                "${jsString(name)}: ${serialize(field)}"
            }

            is TsTestValue.TsNumber -> value.number.toString()
            is TsTestValue.TsBoolean -> value.value.toString()
            is TsTestValue.TsString -> jsString(value.value)
            TsTestValue.TsUndefined -> "undefined"
            else -> error("Unsupported replay value: $value")
        }

        val script = buildString {
            appendLine(getResourcePath("/samples/operators/DeleteProperty.ts").readText())
            tests.forEach { (name, generated) ->
                assertTrue(generated.isNotEmpty(), "$name produced no witnesses")

                generated.forEachIndexed { index, test ->
                    val arguments = test.before.parameters.joinToString { value -> serialize(value) }
                    val expected = serialize(test.returnValue)

                    appendLine("if (new DeleteProperty().$name($arguments) !== $expected) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "delete-input-witnesses",
            timeoutMessage = "Input deletion replay timed out",
            failureContext = script.take(n = 1000),
        )
    }

    @Test
    fun `deleting an own property with a prototype fallback reports unsupported outcome`() {
        val method = getMethod("deleteOwnToString")
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val analysis = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.states.isEmpty())
        assertTrue(analysis.unsupportedPaths.any { "Object.prototype lookup" in it })
    }

    @Test
    fun `deleting prototype methods reports unsupported while own field remains supported`() {
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )
        val prototypeMethod = getMethod("deletePrototypeMethod")
        val arrayMethod = getMethod("deleteArrayPrototypeMethod")
        val ownField = getMethod("deleteClassOwnField")
        val ownMethod = getMethod("deleteObjectLiteralMethod")

        val analyses = TsMachine(scene, options, TsOptions()).use { machine ->
            listOf(prototypeMethod, arrayMethod, ownField, ownMethod).map { method ->
                method to machine.analyzeWithOutcome(methods = listOf(method))
            }.toMap()
        }
        val methodAnalysis = analyses.getValue(prototypeMethod)
        val arrayAnalysis = analyses.getValue(arrayMethod)
        val fieldAnalysis = analyses.getValue(ownField)
        val ownMethodAnalysis = analyses.getValue(ownMethod)

        assertEquals(TsAnalysisStopReason.EXHAUSTED, methodAnalysis.stopReason)
        assertTrue(methodAnalysis.states.isEmpty())
        assertTrue(methodAnalysis.unsupportedPaths.any { "class prototype method" in it })

        assertEquals(TsAnalysisStopReason.EXHAUSTED, arrayAnalysis.stopReason)
        assertTrue(arrayAnalysis.states.isEmpty())
        assertTrue(arrayAnalysis.unsupportedPaths.any { "Array.prototype lookup" in it })

        assertEquals(TsAnalysisStopReason.EXHAUSTED, fieldAnalysis.stopReason)
        assertTrue(fieldAnalysis.unsupportedPaths.isEmpty())
        val fieldResult = fieldAnalysis.states.single().let { state -> TsTestResolver().resolve(ownField, state) }
        assertEquals(1.0, assertIs<TsTestValue.TsNumber>(fieldResult.returnValue).number)

        assertEquals(TsAnalysisStopReason.EXHAUSTED, ownMethodAnalysis.stopReason)
        assertTrue(ownMethodAnalysis.unsupportedPaths.isEmpty())
        val ownMethodResult = ownMethodAnalysis.states.single().let { state -> TsTestResolver().resolve(ownMethod, state) }
        assertEquals(1.0, assertIs<TsTestValue.TsNumber>(ownMethodResult.returnValue).number)

        val script = buildString {
            appendLine(getResourcePath("/samples/operators/DeleteProperty.ts").readText())
            appendLine("if (new DeleteProperty().deletePrototypeMethod() !== 0) throw Error('prototype method');")
            appendLine("if (new DeleteProperty().deleteArrayPrototypeMethod() !== 0) throw Error('array prototype');")
            appendLine("if (new DeleteProperty().deleteClassOwnField() !== 1) throw Error('own field');")
            appendLine("if (new DeleteProperty().deleteObjectLiteralMethod() !== 1) throw Error('own method');")
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "delete-prototype-method",
            timeoutMessage = "Node replay timed out",
        )
    }
}
