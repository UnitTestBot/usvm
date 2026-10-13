package org.usvm.ts.calls

import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class CallsObjectInputsTest : CurrentTsCallsSymbolicEngineTestSupport() {
    @Test
    fun `plain object argument has symbolic fields and exact source replay`() {
        val fixture = fixture(
            source = """
                export interface Input { score: number; enabled: boolean }

                export function accepts(input: Input): boolean {
                  if (input.score > 5 && input.enabled) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(
                name = "input",
                domain = ObjectDomain(mapOf(
                    "score" to NumberDomain(),
                    "enabled" to BooleanDomain,
                )),
            )),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())
        val input = assertIs<JsConcreteValue.Object>(inputs.single())

        assertTrue(assertIs<JsConcreteValue.Number>(input.fields.getValue("score")).toDouble() > 5)
        assertEquals(JsConcreteValue.Boolean(true), input.fields.getValue("enabled"))
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `plain object keeps data fields of a class annotation without becoming that class`() {
        val fixture = fixture(
            source = """
                class C {
                  x: number = 0;
                  f(): number { return 7; }
                }

                export function accepts(input: C): boolean {
                  if (input.x > 5) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(name = "input", domain = ObjectDomain(mapOf("x" to NumberDomain())))),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `plain object does not borrow methods from its class annotation`() {
        val fixture = fixture(
            source = """
                class C {
                  x: number = 0;
                  f(): number { return 7; }
                }

                export function accepts(input: C): boolean {
                  if (input.f() === 7) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(name = "input", domain = ObjectDomain(mapOf("x" to NumberDomain())))),
            targetStatement = "return true;",
        )
        val unknownCalls = mutableListOf<TsUnknownCallEvent>()

        val result = fixture.search(modelIds = emptySet(), unknownCallEventSink = unknownCalls::add)

        assertEquals(CallsSymbolicStatus.UNREACHED, result.status, result.toString())
        assertNull(result.inputs)
        assertTrue(unknownCalls.any { it.failureReason == TsUnknownCallFailureReason.NO_SUITABLE_VIRTUAL_TARGET })
    }

    @Test
    fun `plain object owns only its supplied fields despite the class annotation`() {
        val fixture = fixture(
            source = """
                class C {
                  x: number = 0;
                  missing: number = 7;
                }

                export function accepts(input: C): boolean {
                  if ('x' in input && !('missing' in input) && input.missing === undefined) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(name = "input", domain = ObjectDomain(mapOf("x" to NumberDomain())))),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `plain object is not an instance of its class annotation`() {
        val fixture = fixture(
            source = """
                class C { x: number = 0; }

                export function accepts(input: C): boolean {
                  if (!(input instanceof C)) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(name = "input", domain = ObjectDomain(mapOf("x" to NumberDomain())))),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `plain object constructor lookup cannot invent the annotated constructor`() {
        val fixture = fixture(
            source = """
                class C { x: number = 0; }

                export function accepts(input: C): boolean {
                  if (input.constructor === C) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(name = "input", domain = ObjectDomain(mapOf("x" to NumberDomain())))),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val replay = OriginalTypeScriptTargetReplayer().replay(
            sourceRoots = listOf(fixture.sourceRoot),
            entryPoint = fixture.function.entryPoint,
            inputs = listOf(JsConcreteValue.Object(mapOf("x" to JsConcreteValue.number(1.0)))),
            target = fixture.target,
            timeoutMillis = 10_000L,
        )

        // Current engine reports unsupported Object.prototype lookup through the search error boundary.
        assertEquals(CallsSymbolicStatus.TOOL_ERROR, result.status, result.toString())
        assertTrue(result.diagnostic.orEmpty().contains("unsupported prototype lookup"), result.toString())
        assertNull(result.inputs)
        assertEquals(CallsReplayStatus.REJECTED, replay.status, replay.toString())
    }

    @Test
    fun `object field primitive storage mismatch is rejected before search`() {
        assertUnsupportedObjectField(annotation = "boolean", domain = NumberDomain(), path = "input.value")
    }

    @Test
    fun `object field array element storage mismatch is rejected before search`() {
        assertUnsupportedObjectField(
            annotation = "boolean[]",
            domain = ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 2),
            path = "input.value[]",
        )
    }

    @Test
    fun `object domain cannot pretend to be an array field`() {
        assertUnsupportedObjectField(
            annotation = "number[]",
            domain = ObjectDomain(mapOf("length" to NumberDomain())),
            path = "input.value",
        )
    }

    private fun assertUnsupportedObjectField(annotation: String, domain: PropertyDomain, path: String) {
        val fixture = fixture(
            source = """
                export function accepts(input: { value: $annotation }): boolean {
                  return true;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(name = "input", domain = ObjectDomain(mapOf("value" to domain)))),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED, preflight.reasonCode)
        assertTrue(preflight.diagnostic.orEmpty().contains(path), preflight.toString())
    }

    @Test
    fun `nested object and array fields have symbolic values and exact source replay`() {
        val fixture = fixture(
            source = """
                export function accepts(input: { details: { score: number }; scores: number[] }): boolean {
                  if (input.details.score > 5 && input.scores.length > 0 && input.scores[0] > 3) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(
                PropertyInput(
                    name = "input",
                    domain = ObjectDomain(
                        fields = mapOf(
                            "details" to ObjectDomain(fields = mapOf("score" to NumberDomain())),
                            "scores" to ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 2),
                        ),
                    ),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())
        val input = assertIs<JsConcreteValue.Object>(inputs.single())
        val details = assertIs<JsConcreteValue.Object>(input.fields.getValue("details"))
        val scores = assertIs<JsConcreteValue.Array>(input.fields.getValue("scores"))

        assertTrue(assertIs<JsConcreteValue.Number>(details.fields.getValue("score")).toDouble() > 5)
        assertTrue(assertIs<JsConcreteValue.Number>(scores.elements.first()).toDouble() > 3)
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `nested object domain with non-object EtsIR field is rejected before search`() {
        val fixture = fixture(
            source = """
                export function accepts(input: { details: number }): boolean {
                  return true;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(
                PropertyInput(
                    name = "input",
                    domain = ObjectDomain(
                        fields = mapOf("details" to ObjectDomain(fields = mapOf("score" to NumberDomain()))),
                    ),
                ),
            ),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, preflight.status)
        assertEquals(CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED, preflight.reasonCode)
        assertTrue(preflight.diagnostic.orEmpty().contains("input.details"), preflight.toString())
    }

    @Test
    fun `object argument can pass through constructor and stored callback`() {
        val fixture = fixture(
            source = """
                class Mapper {
                  private callback: (value: number) => number;

                  constructor() {
                    this.callback = (value: number) => value + 1;
                  }

                  map(value: number): number {
                    return this.callback(value);
                  }
                }

                export function accepts(input: { score: number }): boolean {
                  const mapper = new Mapper();
                  if (mapper.map(input.score) > 5) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(
                name = "input",
                domain = ObjectDomain(mapOf("score" to NumberDomain())),
            )),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())
        val input = assertIs<JsConcreteValue.Object>(inputs.single())

        assertTrue(assertIs<JsConcreteValue.Number>(input.fields.getValue("score")).toDouble() > 4)
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `object domain cannot pretend to be a function field`() {
        assertUnsupportedObjectField(
            annotation = "() => number",
            domain = ObjectDomain(mapOf("score" to NumberDomain())),
            path = "input.value",
        )
    }

    @Test
    fun `object inputs compose with recursive arrays and integer domains`() {
        val fixture = fixture(
            source = """
                export function accepts(input: { items: { score: number }[] }): boolean {
                  if (input.items[0].score === 7) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(
                name = "input",
                domain = ObjectDomain(mapOf(
                    "items" to ArrayDomain(
                        element = ObjectDomain(mapOf("score" to IntegerDomain(min = 6, max = 8))),
                        minLength = 1,
                        maxLength = 1,
                    ),
                )),
            )),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `optional object fields preserve entry values after source mutation`() {
        val fixture = fixture(
            source = """
                export function accepts(input: { detail: { score: number } | null }): boolean {
                  if (input.detail === null) return false;
                  const score = input.detail.score;
                  input.detail.score = 0;
                  if (score === 7) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "accepts",
            inputs = listOf(PropertyInput(
                name = "input",
                domain = ObjectDomain(mapOf(
                    "detail" to OptionalDomain(
                        value = ObjectDomain(mapOf("score" to IntegerDomain(min = 6, max = 8))),
                        nil = JsConcreteValue.Null,
                    ),
                )),
            )),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())
        val inputs = assertNotNull(result.inputs, result.toString())
        val input = assertIs<JsConcreteValue.Object>(inputs.single())
        val detail = assertIs<JsConcreteValue.Object>(input.fields.getValue("detail"))

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        assertEquals(JsConcreteValue.number(7.0), detail.fields.getValue("score"))
        fixture.assertReplayConfirmed(inputs)
    }
}
