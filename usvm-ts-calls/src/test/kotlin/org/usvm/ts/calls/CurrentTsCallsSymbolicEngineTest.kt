package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.call.TsUnknownCallDecision
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

private const val ERROR_CONSTRUCTOR_MODEL_ID: String = "ts.error.constructor"

@Suppress("LargeClass")
class CurrentTsCallsSymbolicEngineTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `runtime limitation is reported instead of an unreached target`() {
        val fixture = fixture(
            source = """
                export function writesNamedProperty(value: number): boolean {
                  const values = [1];
                  values[0.5] = value;
                  return true;
                }
            """.trimIndent(),
            exportName = "writesNamedProperty",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )
        val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()

        val result = fixture.search(
            modelIds = emptySet(),
            runtimeLimitationEventSink = limitations::add,
        )

        assertEquals(CallsSymbolicStatus.RUNTIME_LIMITATION, result.status, result.toString())
        assertEquals(TsRuntimeFeatureLimitationReason.ARRAY_NAMED_PROPERTY_WRITE, limitations.single().reason)
        assertTrue(result.diagnostic.orEmpty().contains("ARRAY_NAMED_PROPERTY_WRITE"))
    }

    @Test
    fun `fresh unknown string index branches remain executable and extractable`() {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ~!@#${'$'}%^&*_-+=|:.><?/'"
        val fixture = fixture(
            source = """
                export function encodeNum(n: number): string {
                    const enc = "$alphabet";
                    const base = enc.length;
                    let ret = '';
                    do {
                        ret += enc[n % base];
                        n = Math.floor(n / base);
                    } while (n >= 1);
                    return ret;
                }

                let nUid = 0;

                export function uid(): string {
                    return encodeNum(nUid++);
                }
            """.trimIndent(),
            exportName = "uid",
            inputs = emptyList(),
            targetStatement = "return encodeNum(nUid++);",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "encodeNum(nUid++)",
        )
        val unknownCalls = mutableListOf<TsUnknownCallEvent>()

        val result = fixture.search(
            modelIds = emptySet(),
            profile = CallsExperimentProfile.EMPTY_FRESH,
            unknownCallEventSink = unknownCalls::add,
        )

        assertEquals(CallsSymbolicStatus.REACHED, result.status, "$result; unknownCalls=$unknownCalls")
        assertEquals(emptyList(), result.inputs)
    }

    @Test
    fun `fresh array element can be assigned to numeric object field`() {
        val returnExpression = "{ major: parts[0] || 0, minor: parts[1] || 0, patch: parts[2] || 0 }"
        val targetStatement = "return $returnExpression;"
        val fixture = fixture(
            source = """
                export function parseVersion(version: string): {
                  major: number;
                  minor: number;
                  patch: number;
                } {
                  const parts = version.replace('v', '').split('.').map(Number);
                  $targetStatement
                }
            """.trimIndent(),
            exportName = "parseVersion",
            inputs = listOf(PropertyInput(name = "version", domain = StringDomain())),
            targetStatement = targetStatement,
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = returnExpression,
        )

        val result = fixture.search(
            modelIds = emptySet(),
            profile = CallsExperimentProfile.EMPTY_FRESH,
        )

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        fixture.assertReplayConfirmed(inputs)
    }

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

        val inputs = assertNotNull(result.inputs, "$result; unknownCalls=$unknownCalls")
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
    fun `completed return rejects a partial ordinary return expression`() {
        val fixture = fixture(
            source = "export function increment(value: number): number { return value + 1; }",
            exportName = "increment",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "1",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "1",
        )

        val preflight = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, preflight.status)
        assertEquals(CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNSUPPORTED, preflight.reasonCode)
    }

    @Test
    fun `completed return accepts a module-local arrow exported by clause`() {
        val expression = "value > 0"
        val fixture = fixture(
            source = "const aliasedArrow = (value: number): boolean => $expression; export { aliasedArrow };",
            exportName = "aliasedArrow",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = expression,
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = expression,
        )

        val preflight = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
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
    fun `accepts genuine builtin lexical captures before search`() {
        val fixture = fixture(
            source = """
                export const usesBuiltinGlobals = (value: number): boolean => {
                  const map = new Map<number, Set<number>>([[value, new Set([value])]]);
                  const errors = [new Error(), new RangeError(), new TypeError()];
                  if (Date.now() >= 0 || value === Infinity || value === NaN || isNaN(value)) {
                    return true;
                  }
                  return map.size === errors.length;
                };
            """.trimIndent(),
            exportName = "usesBuiltinGlobals",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, result.status, result.toString())
    }

    @Test
    fun `preflight rejects regex raw entities with a stable reason`() {
        val fixture = fixture(
            source = """
                export function containsA(value: string): boolean {
                  if (/a/.test(value)) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "containsA",
            inputs = listOf(PropertyInput(name = "value", domain = StringDomain(maxLength = 3))),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(CallsSymbolicPreflightReasonCode.REGEX_LITERAL_UNSUPPORTED, result.reasonCode, result.toString())
    }

    @Test
    fun `preflight rejects regex raw entities in a reachable same-file helper`() {
        val fixture = fixture(
            source = """
                function helper(): boolean {
                  return /x/.test("x");
                }

                export function callsHelper(value: number): boolean {
                  return helper() && value > 0;
                }
            """.trimIndent(),
            exportName = "callsHelper",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return helper() && value > 0;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(CallsSymbolicPreflightReasonCode.REGEX_LITERAL_UNSUPPORTED, result.reasonCode, result.toString())
    }

    @Test
    fun `preflight ignores regex raw entities in an unreachable same-file helper`() {
        val fixture = fixture(
            source = """
                function unusedHelper(): boolean {
                  return /x/.test("x");
                }

                export function ignoresHelper(value: number): boolean {
                  if (value > 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "ignoresHelper",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, result.status, result.toString())
    }

    @Test
    fun `preflight rejects spread raw entities with a stable reason`() {
        val fixture = fixture(
            source = """
                export function copies(values: number[]): boolean {
                  const copy = [...values];
                  if (copy.length > 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "copies",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), maxLength = 3),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(CallsSymbolicPreflightReasonCode.SPREAD_UNSUPPORTED, result.reasonCode, result.toString())
    }

    @Test
    fun `preflight rejects destructuring raw entities with a stable reason`() {
        val fixture = fixture(
            source = """
                export function readsHead(values: number[]): boolean {
                  const [head, ...tail] = values;
                  if (head === 1 && tail.length >= 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "readsHead",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), maxLength = 3),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(CallsSymbolicPreflightReasonCode.DESTRUCTURING_UNSUPPORTED, result.reasonCode, result.toString())
    }

    @Test
    fun `preflight rejects nested runtime lexical environments`() {
        val fixture = fixture(
            source = """
                export function invokesClosure(value: number): boolean {
                  const predicate = () => value > 0;
                  if (predicate()) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "invokesClosure",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(
            CallsSymbolicPreflightReasonCode.LEXICAL_ENVIRONMENT_UNSUPPORTED,
            result.reasonCode,
            result.toString(),
        )
    }

    @Test
    fun `preflight rejects prototype property access`() {
        val fixture = fixture(
            source = """
                export function readsPrototype(): boolean {
                  if (Object.prototype !== undefined) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "readsPrototype",
            inputs = emptyList(),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(
            CallsSymbolicPreflightReasonCode.PROTOTYPE_ACCESS_UNSUPPORTED,
            result.reasonCode,
            result.toString(),
        )
    }

    @Test
    fun `preflight rejects symbolic number to string conversion`() {
        val fixture = fixture(
            source = """
                export function formatsValue(value: number): boolean {
                  const text = "value=" + value;
                  if (text.length > 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "formatsValue",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(
            CallsSymbolicPreflightReasonCode.SYMBOLIC_NUMBER_TO_STRING_UNSUPPORTED,
            result.reasonCode,
            result.toString(),
        )
    }

    @Test
    fun `preflight rejects exponentiation before scheduling profiles`() {
        val fixture = fixture(
            source = """
                export function cubeRoot(value: number): boolean {
                  if (value ** (1 / 3) === 2) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "cubeRoot",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(CallsSymbolicPreflightReasonCode.EXPONENTIATION_UNSUPPORTED, result.reasonCode)
    }

    @Test
    fun `preflight checks a global regex initializer reached by a static field read`() {
        val fixture = fixture(
            source = """
                export const pattern = /abc/;
                export function matches(value: string): boolean {
                  if (pattern.test(value)) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "matches",
            inputs = listOf(PropertyInput(name = "value", domain = StringDomain())),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status)
        assertEquals(CallsSymbolicPreflightReasonCode.REGEX_LITERAL_UNSUPPORTED, result.reasonCode)
    }

    @Test
    fun `initializes captured Math for modeled arrow search and replay`() {
        val fixture = fixture(
            source = """
                export const floorsToThree = (value: number): boolean => {
                  if (Math.floor(value) === 3) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "floorsToThree",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )
        val unknownCalls = mutableListOf<TsUnknownCallEvent>()

        val result = fixture.search(
            modelIds = setOf("ts.math.floor"),
            unknownCallEventSink = unknownCalls::add,
        )

        val inputs = assertNotNull(result.inputs, "$result; unknownCalls=$unknownCalls")
        val value = assertIs<JsConcreteValue.Number>(inputs.single()).toDouble()
        assertEquals(3.0, kotlin.math.floor(value))
        assertTrue(
            unknownCalls.any { event ->
                (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId == "ts.math.floor"
            },
        )
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `initializes captured Number for modeled arrow search and replay`() {
        val fixture = fixture(
            source = """
                export const isIntegerThree = (value: number): boolean => {
                  if (Number.isInteger(value) && value > 2 && value < 4) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "isIntegerThree",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )
        val unknownCalls = mutableListOf<TsUnknownCallEvent>()

        val result = fixture.search(
            modelIds = setOf("ts.number.isInteger"),
            unknownCallEventSink = unknownCalls::add,
        )

        val inputs = assertNotNull(result.inputs, "$result; unknownCalls=$unknownCalls")
        assertEquals(3.0, assertIs<JsConcreteValue.Number>(inputs.single()).toDouble())
        assertTrue(
            unknownCalls.any { event ->
                (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId == "ts.number.isInteger"
            },
        )
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `does not model user defined Math and Number receivers`() {
        val fixture = fixture(
            source = """
                class UserMath {
                  floor(value: number): number {
                    return value + 1;
                  }
                }

                class UserNumber {
                  isInteger(value: number): boolean {
                    return value === 2;
                  }
                }

                export function usesShadowedGlobals(value: number): boolean {
                  const Math = new UserMath();
                  const Number = new UserNumber();
                  if (Math.floor(value) === 3 && Number.isInteger(value)) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "usesShadowedGlobals",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )
        val unknownCalls = mutableListOf<TsUnknownCallEvent>()

        val result = fixture.search(
            modelIds = setOf("ts.math.floor", "ts.number.isInteger"),
            unknownCallEventSink = unknownCalls::add,
        )

        val inputs = assertNotNull(result.inputs, "$result; unknownCalls=$unknownCalls")
        assertEquals(2.0, assertIs<JsConcreteValue.Number>(inputs.single()).toDouble())
        assertTrue(unknownCalls.none { event -> event.decision is TsUnknownCallDecision.ModelApplied })
        fixture.assertReplayConfirmed(inputs)
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

    @Test
    fun `Error constructor model reaches throw and native replay preserves the exception`() {
        val fixture = fixture(
            source = """
                export function rejectsZero(value: number): boolean {
                  if (value === 0) {
                    throw new Error('expected message');
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "rejectsZero",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "throw new Error('expected message');",
        )
        val unknownCalls = mutableListOf<TsUnknownCallEvent>()

        val result = fixture.search(
            modelIds = setOf(ERROR_CONSTRUCTOR_MODEL_ID),
            unknownCallEventSink = unknownCalls::add,
        )

        val inputs = assertNotNull(result.inputs, "$result; unknownCalls=$unknownCalls")
        assertTrue(assertIs<JsConcreteValue.Number>(inputs.single()).toDouble() == 0.0)
        assertEquals(
            listOf(ERROR_CONSTRUCTOR_MODEL_ID),
            unknownCalls.mapNotNull { event ->
                (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId
            },
            unknownCalls.toString(),
        )

        val replay = fixture.replay(inputs)
        assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        assertEquals("threw", replay.invocation?.invocation)
        assertEquals(true, replay.invocation?.targetHit)
        assertEquals("Error", replay.invocation?.errorName)
        assertEquals("expected message", replay.invocation?.errorMessage)
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
            profile: CallsExperimentProfile = CallsExperimentProfile.FROZEN_STOP,
            unknownCallEventSink: ((TsUnknownCallEvent) -> Unit)? = null,
            runtimeLimitationEventSink: ((TsRuntimeFeatureLimitationEvent) -> Unit)? = null,
        ): CallsSymbolicSearchResult = engine.search(
            CallsSymbolicSearchRequest(
                sourceRoot = sourceRoot,
                project = project,
                function = function,
                target = target,
                profile = profile,
                frozenModelIds = modelIds,
                expectedNativeFrontendRevision = "bundled:test",
                seed = 0,
                budget = 10.seconds,
                unknownCallEventSink = unknownCallEventSink,
                runtimeLimitationEventSink = runtimeLimitationEventSink,
            )
        )

        fun assertReplayConfirmed(inputs: List<JsConcreteValue>) {
            val replay = replay(inputs)

            assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
        }

        fun replay(inputs: List<JsConcreteValue>): CallsSourceReplayResult =
            OriginalTypeScriptTargetReplayer().replay(
                sourceRoots = listOf(sourceRoot),
                entryPoint = function.entryPoint,
                inputs = inputs,
                target = target,
                timeoutMillis = 10_000L,
            )
    }
}
