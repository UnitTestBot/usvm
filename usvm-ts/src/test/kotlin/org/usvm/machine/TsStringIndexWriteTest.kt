package org.usvm.machine

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import org.usvm.util.jsString
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TsStringIndexWriteTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val strictSource = getResourcePath("/models/StringIndexWrites.ts")
    private val sloppySource = getResourcePath("/models/StringIndexWritesSloppy.ts")
    private val strictFile = loadEtsFileAutoConvert(strictSource, provider = EtsIrProvider.TS_FRONTEND)
    private val sloppyFile = loadEtsFileAutoConvert(sloppySource, provider = EtsIrProvider.TS_FRONTEND)
    override val scene = EtsScene(listOf(strictFile, sloppyFile))
    override val tsOptions = TsOptions(maxArraySize = 4)
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            val analysis = machine.analyzeWithOutcome(listOf(method))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
            assertTrue(analysis.unsupportedPaths.isEmpty(), "${method.name}: ${analysis.unsupportedPaths}")
            analysis.states.map { state -> TsTestResolver().resolve(method, state) }
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
    fun `strict symbolic writes throw after evaluating the right side and preserve the string`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method("strictCaught"),
            { _, index, result -> index.number == 0.0 && result.number == 1.0 },
            { _, index, result -> index.number != 0.0 && result.number == 2.0 },
            invariants = arrayOf({ _, index, result -> result.number == if (index.number == 0.0) 1.0 else 2.0 }),
        )
    }

    @Test
    fun `strict writes fail for canonical invalid and length property keys`() {
        discoverProperties<TsTestValue.TsBoolean>(
            method = method("strictKeys"),
            { result -> result.value },
            invariants = arrayOf({ result -> result.value }),
        )
    }

    @Test
    fun `a right side exception precedes the string write error`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method("rightSideThrows"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `sloppy symbolic writes ignore the assignment and preserve its value and effects`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsBoolean>(
            method = method("sloppyWrite", sloppy = true),
            { input, _, result -> input.value.isEmpty() && result.value },
            { input, _, result -> input.value.isNotEmpty() && result.value },
            invariants = arrayOf({ _, _, result -> result.value }),
        )
    }

    @Test
    fun `sloppy keys compound assignment and updates retain immutable string storage`() {
        for (name in listOf("sloppyKeys", "sloppyCompound", "sloppyUpdates", "sloppyWhitespaceUpdate")) {
            discoverProperties<TsTestValue.TsBoolean>(
                method = method(name, sloppy = true),
                { result -> result.value },
                invariants = arrayOf({ result -> result.value }),
            )
        }
    }

    @Test
    fun `symbolic string updates convert digits whitespace and other code units`() {
        val whitespace = setOf(
            '\t', '\n', '\u000b', '\u000c', '\r', ' ', '\u00a0', '\u1680',
            '\u2028', '\u2029', '\u202f', '\u205f', '\u3000', '\ufeff',
        ) + ('\u2000'..'\u200a')

        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method("sloppySymbolicUpdate", sloppy = true),
            { input, result -> input.value.length == 1 && input.value[0] in '0'..'9' && !result.number.isNaN() },
            { input, result -> input.value == " " && result.number == 0.0 },
            { _, result -> result.number.isNaN() },
            invariants = arrayOf({ input, result ->
                val value = input.value
                val expected = when {
                    value.length != 1 -> Double.NaN
                    value[0] in '0'..'9' -> value[0].digitToInt().toDouble()
                    value[0] in whitespace -> 0.0
                    else -> Double.NaN
                }

                result.number == expected || result.number.isNaN() && expected.isNaN()
            }),
        )
    }

    @Test
    fun `source directives apply to functions and lexically nested calls`() {
        val methodNames = listOf(
            "ownStrict",
            "inheritedStrict",
            "strictCaller",
            "sloppyCaller",
            "inheritedArrow",
            "classInScript",
        )

        for (name in methodNames) {
            discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
                method = method(name, sloppy = true),
                { _, result -> result.number == 1.0 },
                invariants = arrayOf({ _, result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `an uncaught strict write returns a TypeError object`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber, TsTestValue.TsException.ObjectException>(
            method = method("uncaught"),
            { _, _, result -> assertIs<TsTestValue.TsClass>(result.value).name == "TypeError" },
        )
    }

    @Test
    fun `legacy string writes without lexical metadata report unsupported`() {
        val source = directory.resolve("legacy-string-write.ts")
        source.writeText("function legacy() { const target: any = 'abc'; target[0] = 'x'; return 1; }")
        val output = generateEtsIR(source, isProject = false, timeout = null, provider = EtsIrProvider.TS_FRONTEND)
        val dto = EtsFileDto.loadFromJson(output.readText())
        val legacyClasses = dto.classes.map { clazz ->
            val legacyMethods = clazz.methods.map { it.copy(isStrictMode = null) }
            clazz.copy(methods = legacyMethods)
        }
        val legacyDto = dto.copy(classes = legacyClasses)
        val legacyFile = legacyDto.toEtsFile()
        val legacyMethod = legacyFile.allClasses.flatMap { it.methods }.single { it.name == "legacy" }
        val legacyScene = EtsScene(listOf(legacyFile))
        val machineOptions = options.copy(throwExceptionOnStepFailure = false)

        val analysis = TsMachine(scene = legacyScene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyzeWithOutcome(listOf(legacyMethod))
        }

        assertTrue(analysis.states.isEmpty())
        assertEquals(listOf("String property writes require lexical strictness metadata"), analysis.unsupportedPaths)
    }

    @Test
    fun `generated strict and sloppy write witnesses replay in JavaScript`() {
        replay(
            source = strictSource,
            methodNames = listOf("strictCaught", "strictKeys", "rightSideThrows", "uncaught"),
            sloppy = false,
        )
        replay(
            source = sloppySource,
            methodNames = listOf(
                "sloppyWrite",
                "sloppyKeys",
                "sloppyCompound",
                "sloppyUpdates",
                "sloppySymbolicUpdate",
                "sloppyWhitespaceUpdate",
                "ownStrict",
                "inheritedStrict",
                "strictCaller",
                "sloppyCaller",
                "inheritedArrow",
                "classInScript",
            ),
            sloppy = true,
        )
    }

    private fun replay(source: Path, methodNames: List<String>, sloppy: Boolean) {
        val script = buildString {
            appendLine(source.readText())
            for (name in methodNames) {
                for (test in runner(method(name, sloppy = sloppy), options)) {
                    val arguments = test.before.parameters.joinToString { value -> jsValue(value) }
                    val receiver = if (sloppy) "" else "new StringIndexWrites()."
                    val call = "$receiver$name($arguments)"
                    if (test.returnValue is TsTestValue.TsException.ObjectException) {
                        appendLine("{ let caught = false; try { $call; }")
                        appendLine("catch (error) { caught = error instanceof TypeError; }")
                        appendLine("if (!caught) throw Error('$name TypeError'); }")
                    } else {
                        val expected = jsValue(test.returnValue)

                        appendLine("if (!Object.is($call, $expected)) throw Error('$name');")
                    }
                }
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = if (sloppy) "string-writes-sloppy" else "string-writes-strict",
            timeoutMessage = "String write replay timed out",
            failureContext = script.take(1000),
        )
    }

    private fun method(name: String, sloppy: Boolean = false): EtsMethod {
        val file = if (sloppy) sloppyFile else strictFile
        val className = if (sloppy) DEFAULT_ARK_CLASS_NAME else "StringIndexWrites"

        return file.allClasses.single { it.name == className }.methods.single { it.name == name }
    }

    private fun jsValue(value: TsTestValue): String = when (value) {
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsBoolean -> value.value.toString()
        else -> assertIs<TsTestValue.TsNumber>(value).number.toString()
    }
}
