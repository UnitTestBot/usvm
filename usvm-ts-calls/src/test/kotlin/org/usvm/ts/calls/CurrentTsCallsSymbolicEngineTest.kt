package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CurrentTsCallsSymbolicEngineTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `bundled frontend accepts only the revision baked into the running build`() {
        val engine = CurrentTsCallsSymbolicEngine(
            environment = emptyMap<String, String>()::get,
            bundledNativeFrontendRevision = "bundled:published-jacodb",
        )

        engine.verifyNativeFrontendOnce(expectedRevision = "bundled:published-jacodb")

        assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontendOnce(expectedRevision = "bundled:different-jacodb")
        }
    }

    @Test
    fun `bundled frontend rejects native frontend environment overrides`() {
        val environment = mapOf("ETS_FRONTEND_DIR" to "/unused/frontend")
        val engine = CurrentTsCallsSymbolicEngine(
            environment = environment::get,
            bundledNativeFrontendRevision = "bundled:published-jacodb",
        )

        assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontendOnce(expectedRevision = "bundled:published-jacodb")
        }
    }

    @Test
    fun `extracts nonempty generic number array containing zero and replays source`() {
        val fixture = fixture(
            source = """
                export function includesZero<T>(values: T[], value: T): boolean {
                  if (value === 0 && values.indexOf(value) >= 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "includesZero",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 3),
                ),
                PropertyInput(name = "value", domain = NumberDomain()),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = setOf("ts.array.indexOf", "ts.math.floor"))

        val inputs = assertNotNull(result.inputs, result.toString())
        val values = assertIs<JsConcreteValue.Array>(inputs[0]).elements
        val searchValue = assertIs<JsConcreteValue.Number>(inputs[1]).toDouble()
        assertTrue(values.isNotEmpty())
        assertTrue(searchValue == 0.0)
        assertTrue(values.any { element -> assertIs<JsConcreteValue.Number>(element).toDouble() == 0.0 })
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `extracts nonempty number array with absent NaN and replays source`() {
        val fixture = fixture(
            source = """
                export function excludesNaN(values: number[], value: number): boolean {
                  if (values.length > 0 && Number.isNaN(value) && values.indexOf(value) < 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "excludesNaN",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 3),
                ),
                PropertyInput(name = "value", domain = NumberDomain()),
            ),
            targetStatement = "return true;",
        )

        val unknownCalls = mutableListOf<TsUnknownCallEvent>()
        val result = fixture.search(
            modelIds = setOf("ts.array.indexOf", "ts.math.floor", "ts.number.isNaN"),
            unknownCallEventSink = unknownCalls::add,
        )

        val inputs = assertNotNull(result.inputs, result.toString())
        assertTrue(assertIs<JsConcreteValue.Array>(inputs[0]).elements.isNotEmpty())
        assertTrue(assertIs<JsConcreteValue.Number>(inputs[1]).toDouble().isNaN())
        assertTrue(unknownCalls.isNotEmpty())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `extracts constrained UTF-16 string and replays source`() {
        val fixture = fixture(
            source = """
                export function isGrinningFace(value: string): boolean {
                  if (value.length === 2 && value.charCodeAt(0) === 0xD83D && value.charCodeAt(1) === 0xDE00) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "isGrinningFace",
            inputs = listOf(
                PropertyInput(name = "value", domain = StringDomain(minLength = 1, maxLength = 4)),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(
            modelIds = setOf(
                "ts.string.charCodeAt",
                "ts.string.primitive.codeUnitAt",
                "ts.string.primitive.length",
            ),
        )

        val inputs = assertNotNull(result.inputs, result.toString())
        val value = assertIs<JsConcreteValue.String>(inputs.single()).value
        assertEquals(2, value.length)
        assertEquals("😀", value)
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `extracts nonempty string required by value equality and replays source`() {
        val fixture = fixture(
            source = """
                export function equalsA(value: string): boolean {
                  if (value === "a") {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "equalsA",
            inputs = listOf(
                PropertyInput(name = "value", domain = StringDomain(minLength = 1, maxLength = 4)),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        val inputs = assertNotNull(result.inputs, result.toString())
        assertEquals("a", assertIs<JsConcreteValue.String>(inputs.single()).value)
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `completed return observes normal expression-bodied arrow completion`() {
        val expression = "value > 0"
        val fixture = fixture(
            source = "export const isPositive = (value: number): boolean => $expression;",
            exportName = "isPositive",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = expression,
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = expression,
        )

        val preflight = fixture.preflight()
        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        val result = fixture.search(modelIds = emptySet())

        val inputs = assertNotNull(result.inputs, result.toString())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `completed return rejects a partial expression target without an origin`() {
        val fixture = fixture(
            source = "export const isPositive = (value: number): boolean => value > 0;",
            exportName = "isPositive",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "0",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "0",
        )

        val preflight = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, preflight.status)
        assertEquals(CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNSUPPORTED, preflight.reasonCode)
    }

    @Test
    fun `completed return rejects the first line of a multiline arrow expression`() {
        val fixture = fixture(
            source = """
                export const increment = (value: number): number => value
                  + 1;
            """.trimIndent(),
            exportName = "increment",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "value\n",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "value\n",
        )

        val preflight = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, preflight.status)
        assertEquals(CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNSUPPORTED, preflight.reasonCode)
    }

    @Test
    fun `preflight rejects source coordinates that do not match offsets`() {
        val fixture = fixture(
            source = "export function identity(value: number): number { return value; }",
            exportName = "identity",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return value;",
        )
        val malformedTarget = fixture.target.copy(
            start = fixture.target.start.copy(column = fixture.target.start.column + 1),
        )

        val preflight = fixture.copy(target = malformedTarget).preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNMAPPED, preflight.status)
        assertEquals(CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNMAPPED, preflight.reasonCode)
    }

    @Test
    fun `preflight rejects return expression offsets outside the target`() {
        val fixture = fixture(
            source = "export function identity(value: number): number { return value; }",
            exportName = "identity",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return value;",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
        )
        val malformedTarget = fixture.target.copy(
            returnExpressionStartOffset = fixture.target.endOffset,
            returnExpressionEndOffset = fixture.target.endOffset + 1,
        )

        val preflight = fixture.copy(target = malformedTarget).preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNMAPPED, preflight.status)
        assertEquals(CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNMAPPED, preflight.reasonCode)
    }

    @Test
    fun `rejects arbitrary lexical runtime capture before search`() {
        val fixture = fixture(
            source = """
                let capturesThreshold: (value: number) => boolean;
                {
                  const threshold = 3;
                  capturesThreshold = (value: number): boolean => value > threshold;
                }
                export { capturesThreshold };
            """.trimIndent(),
            exportName = "capturesThreshold",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "value > threshold",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(CallsSymbolicPreflightReasonCode.LEXICAL_CAPTURE_UNSUPPORTED, result.reasonCode)
        assertTrue(result.diagnostic.orEmpty().contains("threshold"))
    }

    @Test
    fun `extracts original array before mutating shift and replays source`() {
        val fixture = fixture(
            source = """
                export function removesSeventeen(values: number[]): boolean {
                  const removed = values.shift();
                  if (removed === 17) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "removesSeventeen",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 3),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = setOf("ts.array.shift"))

        val inputs = assertNotNull(result.inputs, result.toString())
        val values = assertIs<JsConcreteValue.Array>(inputs.single()).elements
        assertTrue(values.isNotEmpty())
        assertEquals(17.0, assertIs<JsConcreteValue.Number>(values.first()).toDouble())
        fixture.assertReplayConfirmed(inputs)
    }

    private fun fixture(
        source: String,
        exportName: String,
        inputs: List<PropertyInput>,
        targetStatement: String,
        targetMode: CallsSourceTargetMode = CallsSourceTargetMode.ENTRY,
        returnExpression: String? = null,
    ): SymbolicFixture {
        val sourceRoot = Files.createDirectory(directory.resolve(exportName))
        val sourceFile = sourceRoot.resolve("Fixture.ts")
        Files.writeString(sourceFile, source)
        runGit(sourceRoot, "init")
        runGit(sourceRoot, "config", "user.name", "USVM Tests")
        runGit(sourceRoot, "config", "user.email", "usvm@example.test")
        runGit(sourceRoot, "add", "Fixture.ts")
        runGit(sourceRoot, "commit", "-m", "fixture")
        val revision = runGit(sourceRoot, "rev-parse", "HEAD").trim()
        val targetStart = source.indexOf(targetStatement)
        check(targetStart >= 0) { "Missing target statement: $targetStatement" }
        val targetEnd = targetStart + targetStatement.length
        val returnExpressionStart = returnExpression?.let { expression ->
            source.indexOf(expression, startIndex = targetStart).takeIf { offset ->
                offset in targetStart until targetEnd
            }
                ?: error("Missing return expression inside target: $expression")
        }
        val target = CallsSourceTarget(
            targetId = "$exportName#$targetStatement",
            siteId = "$exportName:$targetStart:$targetEnd",
            sourcePath = "Fixture.ts",
            startOffset = targetStart,
            endOffset = targetEnd,
            start = sourcePositionAt(source = source, offset = targetStart),
            end = sourcePositionAt(source = source, offset = targetEnd),
            mode = targetMode,
            returnExpressionStartOffset = returnExpressionStart,
            returnExpressionEndOffset = returnExpressionStart?.plus(requireNotNull(returnExpression).length),
        )
        val function = CallsFunctionCase(
            functionId = exportName,
            sourceFile = "Fixture.ts",
            entryPoint = TypeScriptEntryPoint(module = "Fixture.ts", exportName = exportName),
            inputs = inputs,
            targets = listOf(target),
        )
        val project = CallsProjectCase(
            projectId = exportName,
            revision = revision,
            sourceRoot = ".",
            development = true,
            functions = listOf(function),
        )

        return SymbolicFixture(
            sourceRoot = sourceRoot,
            project = project,
            function = function,
            target = target,
        )
    }

    private fun runGit(directory: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "-C", directory.toString()) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Git ${arguments.joinToString()} failed: $output" }
        return output
    }

    private data class SymbolicFixture(
        val sourceRoot: Path,
        val project: CallsProjectCase,
        val function: CallsFunctionCase,
        val target: CallsSourceTarget,
    ) {
        private val engine = CurrentTsCallsSymbolicEngine(
            environment = emptyMap<String, String>()::get,
            bundledNativeFrontendRevision = "bundled:test",
        )

        fun preflight(): CallsSymbolicPreflightResult = engine.preflight(
            CallsSymbolicPreflightRequest(
                sourceRoot = sourceRoot,
                project = project,
                function = function,
                target = target,
                expectedNativeFrontendRevision = "bundled:test",
            )
        )

        fun search(
            modelIds: Set<String>,
            unknownCallEventSink: ((TsUnknownCallEvent) -> Unit)? = null,
        ): CallsSymbolicSearchResult = engine.search(
            CallsSymbolicSearchRequest(
                sourceRoot = sourceRoot,
                project = project,
                function = function,
                target = target,
                profile = CallsExperimentProfile.FROZEN_STOP,
                frozenModelIds = modelIds,
                expectedNativeFrontendRevision = "bundled:test",
                seed = 0,
                budget = 10.seconds,
                unknownCallEventSink = unknownCallEventSink,
            )
        )

        fun assertReplayConfirmed(inputs: List<JsConcreteValue>) {
            val replay = OriginalTypeScriptTargetReplayer().replay(
                sourceRoots = listOf(sourceRoot),
                entryPoint = function.entryPoint,
                inputs = inputs,
                target = target,
                timeoutMillis = 10_000L,
            )

            assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        }
    }
}
