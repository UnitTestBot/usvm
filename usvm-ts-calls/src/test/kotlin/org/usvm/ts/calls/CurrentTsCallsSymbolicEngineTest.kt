package org.usvm.ts.calls

import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CurrentTsCallsSymbolicEngineTest : CurrentTsCallsSymbolicEngineTestSupport() {
    @Test
    fun `bundled frontend accepts only the revision baked into the running build`() {
        val engine = CurrentTsCallsSymbolicEngine(
            environment = emptyMap<String, String>()::get,
            bundledNativeFrontendRevision = "bundled:published-jacodb",
        )

        engine.verifyNativeFrontend(expectedRevision = "bundled:published-jacodb")

        assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontend(expectedRevision = "bundled:different-jacodb")
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
            engine.verifyNativeFrontend(expectedRevision = "bundled:published-jacodb")
        }
    }

    @Test
    fun `bundled frontend rejects native frontend directory system property`() {
        val propertyName = "ets.frontend.dir"
        val previousValue = System.getProperty(propertyName)
        val engine = CurrentTsCallsSymbolicEngine(
            environment = emptyMap<String, String>()::get,
            bundledNativeFrontendRevision = "bundled:published-jacodb",
        )

        System.setProperty(propertyName, "/unused/frontend")
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                engine.verifyNativeFrontend(expectedRevision = "bundled:published-jacodb")
            }

            assertTrue(failure.message.orEmpty().contains(propertyName))
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, previousValue)
            }
        }
    }

    @Test
    fun `external frontend verifies generated script and library bytes`() {
        val frontend = externalFrontendFixture()
        val identity = callsExternalFrontendIdentity(frontend)
        val revision = runGit(frontend, "rev-parse", "HEAD").trim()
        val engine = CurrentTsCallsSymbolicEngine(
            environment = mapOf("ETS_FRONTEND_DIR" to frontend.toString())::get,
        )

        engine.verifyNativeFrontend(expectedRevision = identity)

        val malformedIdentity = assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontend(expectedRevision = revision)
        }
        assertTrue(malformedIdentity.message.orEmpty().contains("@sha256:"))

        Files.writeString(frontend.resolve("dist/index.js"), "different script")
        val changedScript = assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontend(expectedRevision = identity)
        }
        assertTrue(changedScript.message.orEmpty().contains("runtime digest differs"))

        Files.writeString(frontend.resolve("dist/index.js"), "original script")
        Files.writeString(frontend.resolve("dist/lib.d.ts"), "different library")
        val changedLibrary = assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontend(expectedRevision = identity)
        }
        assertTrue(changedLibrary.message.orEmpty().contains("runtime digest differs"))
    }

    @Test
    fun `external frontend environment directory takes precedence over system property`() {
        val frontend = externalFrontendFixture()
        val identity = callsExternalFrontendIdentity(frontend)
        val propertyName = "ets.frontend.dir"
        val previousValue = System.getProperty(propertyName)
        val engine = CurrentTsCallsSymbolicEngine(
            environment = mapOf("ETS_FRONTEND_DIR" to frontend.toString())::get,
        )

        System.setProperty(propertyName, "/unused/frontend")
        try {
            engine.verifyNativeFrontend(expectedRevision = identity)
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName)
            } else {
                System.setProperty(propertyName, previousValue)
            }
        }
    }

    @Test
    fun `frontend identity command returns frozen external source and runtime identity`() {
        val frontend = externalFrontendFixture()

        val identity = frontendIdentity(args = listOf(frontend.toString()))

        assertEquals(callsExternalFrontendIdentity(frontend), identity)
        assertTrue(identity.matches(Regex("[0-9a-f]{40}@sha256:[0-9a-f]{64}")))
    }

    private fun externalFrontendFixture(): Path {
        val frontend = Files.createDirectory(directory.resolve("frontend"))
        Files.writeString(frontend.resolve(".gitignore"), "dist/\n")
        Files.writeString(frontend.resolve("source.ts"), "export const value = 1;\n")
        runGit(frontend, "init")
        runGit(frontend, "config", "user.name", "USVM Tests")
        runGit(frontend, "config", "user.email", "usvm@example.test")
        runGit(frontend, "add", ".")
        runGit(frontend, "commit", "-m", "Create frontend fixture")

        val dist = Files.createDirectory(frontend.resolve("dist"))
        Files.writeString(dist.resolve("index.js"), "original script")
        Files.writeString(dist.resolve("lib.d.ts"), "original library")
        return frontend
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
    fun `completed return searches and replays a normal arrow`() {
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
        val replay = OriginalTypeScriptTargetReplayer().replay(
            sourceRoots = listOf(fixture.sourceRoot),
            entryPoint = fixture.function.entryPoint,
            inputs = inputs,
            target = fixture.target,
            timeoutMillis = 10_000L,
        )

        assertEquals(CallsReplayStatus.CONFIRMED, replay.status, replay.toString())
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
    fun `supports immutable literal captures from a block`() {
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
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            returnExpression = "value > threshold",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
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
    fun `input domain capacity preserves existing slots beyond the default machine limit`() {
        val fixture = fixture(
            source = """
                export function existingHighSlot(values: number[]): boolean {
                  values[1001] = 7;
                  if (values.length === 1002 && values[1001] === 7) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "existingHighSlot",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(
                        element = NumberDomain(
                            min = JsNumber.finite(0.0),
                            max = JsNumber.finite(0.0),
                            allowNaN = false,
                        ),
                        minLength = 1002,
                        maxLength = 1002,
                    ),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        assertEquals(1002, assertIs<JsConcreteValue.Array>(inputs.single()).elements.size)
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `genuine captured Date reaches calls fallback and replays with native owner identity`() {
        val fixture = fixture(
            source = """
                export const nativeDate = (value: number): boolean => {
                  if (value === 0 && Date.now() > 0) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "nativeDate",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val unknownCalls = mutableListOf<TsUnknownCallEvent>()
        val result = fixture.search(
            modelIds = emptySet(),
            profile = CallsExperimentProfile.FROZEN_FRESH,
            unknownCallEventSink = unknownCalls::add,
        )

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        assertTrue(unknownCalls.any { event -> event.callee.name == "now" })
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }

    @Test
    fun `source Date object keeps its project method and is not replaced by a builtin`() {
        val fixture = fixture(
            source = """
                const Date = { now: (): number => 7 };
                export const userDate = (value: number): boolean => {
                  if (value === 0 && Date.now() > 0) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "userDate",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }

    @Test
    fun `all same-sort array inputs retain dense snapshots after initialization`() {
        val zeroNumbers = NumberDomain(
            min = JsNumber.finite(0.0),
            max = JsNumber.finite(0.0),
            allowNaN = false,
        )
        val arrays = ArrayDomain(element = zeroNumbers, minLength = 1, maxLength = 2)
        val fixture = fixture(
            source = """
                export function bothArraysContainZero(first: number[], second: number[]): boolean {
                  if (first.includes(0) && second.includes(0)) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "bothArraysContainZero",
            inputs = listOf(
                PropertyInput(name = "first", domain = arrays),
                PropertyInput(name = "second", domain = arrays),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = setOf("ts.array.includes", "ts.math.floor"))

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }

    @Test
    fun `immutable module captures named Infinity and NaN preserve source numbers`() {
        val fixture = fixture(
            source = """
                const Infinity = 7;
                const NaN = 8;
                export const sourceNumbers = (value: number): boolean => {
                  if (value === 0 && Infinity === 7 && NaN === 8) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "sourceNumbers",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }

    @Test
    fun `project Math function and literal capture aliases keep runtime values`() {
        val fixture = fixture(
            source = """
                const Math = (value: number): number => value + 7;
                const threshold = 7;
                const alias = threshold;
                export const sourceMath = (value: number): boolean => {
                  if (value === 0 && Math(value) === alias) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "sourceMath",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }

    @Test
    fun `source object capture aliases preserve reference identity`() {
        val fixture = fixture(
            source = """
                const source = { value: 7 };
                const alias = source;
                export const objectAlias = (value: number): boolean => {
                  if (value === 0 && source === alias && source.value === 7) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "objectAlias",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }

    @Test
    fun `genuine SDK Number owner and Infinity NaN values retain their original identities`() {
        val fixture = fixture(
            source = """
                export const sdkValues = (value: number): boolean => {
                  if (value === 0 && Infinity > 7 && Number.isNaN(NaN)) {
                    return true;
                  }
                  return false;
                };
            """.trimIndent(),
            exportName = "sdkValues",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = setOf("ts.number.isNaN"))

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }
}
