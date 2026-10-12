package org.usvm.ts.calls

import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CallsReadinessAndTelemetryTest : CurrentTsCallsSymbolicEngineTestSupport() {
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
    fun `ordinary own prototype property is not rejected by a source name blacklist`() {
        val fixture = fixture(
            source = """
                export function ownPrototype(value: number): boolean {
                  const owner = { prototype: value };
                  if (owner.prototype === 7) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "ownPrototype",
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
    fun `lowered destructuring and literal spread are not rejected by syntax alone`() {
        val fixture = fixture(
            source = """
                export function loweredPatterns(value: number): boolean {
                  const [first] = [value];
                  const values = [first, ...[2, 3]];
                  if (values[0] === 7) return true;
                  return false;
                }
            """.trimIndent(),
            exportName = "loweredPatterns",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }
}
