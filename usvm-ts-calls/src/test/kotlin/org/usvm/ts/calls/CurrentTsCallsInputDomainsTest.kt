package org.usvm.ts.calls

import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.PropertyInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CurrentTsCallsInputDomainsTest : CurrentTsCallsSymbolicEngineTestSupport() {
    @Test
    fun `object input domains produce field witnesses after symbolic materialization`() {
        val fixture = fixture(
            source = """
                export interface ObjectInput { count: number }

                export function objectInput(value: ObjectInput): boolean {
                  if (value.count === 1) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "objectInput",
            inputs = listOf(
                PropertyInput(
                    name = "value",
                    domain = ObjectDomain(fields = mapOf("count" to IntegerDomain(min = 0, max = 1))),
                ),
            ),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())

        val inputs = assertNotNull(result.inputs, result.toString())
        val value = assertIs<JsConcreteValue.Object>(inputs.single())
        assertEquals(JsConcreteValue.number(1.0), value.fields.getValue("count"))
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `structured constants remain outside primitive symbolic domains`() {
        val constant = ConstantDomain(value = JsConcreteValue.Object(fields = emptyMap()))
        val inputs = listOf(PropertyInput(name = "value", domain = constant))

        val diagnostic = callsSymbolicInputPreflight(inputs)

        assertTrue(!constant.isSupportedCallsSymbolicDomain())
        assertNotNull(diagnostic)
        assertTrue(diagnostic.contains("value"))
    }

    @Test
    fun `singleton zero input domain preserves a negative zero witness`() {
        val fixture = fixture(
            source = """
                export function negativeZero(value: number): boolean {
                  if (value === 0 && 1 / value < 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "negativeZero",
            inputs = listOf(
                PropertyInput(
                    name = "value",
                    domain = NumberDomain(
                        min = JsNumber.finite(0.0),
                        max = JsNumber.finite(0.0),
                        allowNaN = false,
                    ),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        val value = assertIs<JsConcreteValue.Number>(inputs.single()).toDouble()
        assertEquals((-0.0).toRawBits(), value.toRawBits())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `number domain cannot replace a declared string parameter`() {
        val fixture = fixture(
            source = """
                export function text(value: string): boolean {
                  if (value.length > 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "text",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED, preflight.reasonCode)
        assertTrue(preflight.diagnostic.orEmpty().contains("inputs.value"))
        assertTrue(preflight.diagnostic.orEmpty().contains("stack slot 1"))
        assertEquals(CallsSymbolicStatus.UNSUPPORTED, result.status, result.toString())
    }

    @Test
    fun `generic array bounds reject an incompatible element domain before solving`() {
        val fixture = fixture(
            source = """
                export function bounded<T extends string>(values: T[]): boolean {
                  if (values.length > 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "bounded",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = NumberDomain(), minLength = 1, maxLength = 2),
                ),
            ),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED, preflight.reasonCode)
        assertTrue(preflight.diagnostic.orEmpty().contains("inputs.values[]"))
        assertTrue(preflight.diagnostic.orEmpty().contains("string"))
        assertEquals(CallsSymbolicStatus.UNSUPPORTED, result.status, result.toString())
    }

    @Test
    fun `any parameter preserves a numeric domain runtime kind and replays source`() {
        val fixture = fixture(
            source = """
                export function actualNumber(value: any): boolean {
                  if (typeof value === "number" && value === 7) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "actualNumber",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val preflight = fixture.preflight()
        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicPreflightStatus.ELIGIBLE, preflight.status, preflight.toString())
        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        assertEquals(7.0, assertIs<JsConcreteValue.Number>(inputs.single()).toDouble())
        fixture.assertReplayConfirmed(inputs)
    }
}
