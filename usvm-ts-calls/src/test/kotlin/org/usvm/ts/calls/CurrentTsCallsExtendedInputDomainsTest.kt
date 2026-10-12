package org.usvm.ts.calls

import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class CurrentTsCallsExtendedInputDomainsTest : CurrentTsCallsSymbolicEngineTestSupport() {
    @Test
    fun `integer domain reaches an integral witness and replays source`() {
        val fixture = fixture(
            source = """
                export function integral(value: number): boolean {
                  if (value === 7) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "integral",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -10, max = 10))),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        assertEquals(7.0, assertIs<JsConcreteValue.Number>(inputs.single()).toDouble())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `integer domain cannot reach negative zero`() {
        val fixture = fixture(
            source = """
                export function integerZero(value: number): boolean {
                  if (value === 0 && 1 / value < 0) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "integerZero",
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -1, max = 1))),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicStatus.UNREACHED, result.status, result.toString())
    }

    @Test
    fun `primitive singleton constants retain exact runtime kinds and source replay`() {
        val fixture = fixture(
            source = """
                export function singleton(value: any): boolean {
                  if (typeof value === "string" && value === "xy") {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "singleton",
            inputs = listOf(PropertyInput(name = "value", domain = ConstantDomain(JsConcreteValue.String("xy")))),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        assertEquals(JsConcreteValue.String("xy"), inputs.single())
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `optional numeric domain preserves both nullish and numeric branches`() {
        for (targetValue in listOf(JsConcreteValue.Undefined, JsConcreteValue.number(7.0))) {
            val condition = if (targetValue == JsConcreteValue.Undefined) "value === undefined" else "value === 7"
            val fixture = fixture(
                source = """
                    export function optional(value: number | undefined): boolean {
                      if ($condition) {
                        return true;
                      }
                      return false;
                    }
                """.trimIndent(),
                exportName = "optional",
                inputs = listOf(
                    PropertyInput(name = "value", domain = OptionalDomain(IntegerDomain(min = 0, max = 10))),
                ),
                targetStatement = "return true;",
            )

            val result = fixture.search(modelIds = emptySet())

            assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
            val inputs = assertNotNull(result.inputs, result.toString())
            assertEquals(targetValue, inputs.single())
            fixture.assertReplayConfirmed(inputs)
        }
    }

    @Test
    fun `optional domain cannot inject undefined into a required numeric parameter`() {
        val fixture = fixture(
            source = """
                export function required(value: number): boolean {
                  if (value === 7) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "required",
            inputs = listOf(PropertyInput(name = "value", domain = OptionalDomain(IntegerDomain()))),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status, result.toString())
        assertEquals(CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED, result.reasonCode)
        assertTrue(result.diagnostic.orEmpty().contains("inputs.value.nil"))
    }

    @Test
    fun `string array values retain symbolic UTF16 backing and source replay`() {
        val fixture = fixture(
            source = """
                export function stringArray(values: string[]): boolean {
                  if (values.length === 1 && values[0] === "xy") {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "stringArray",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(
                        element = StringDomain(minLength = 0, maxLength = 2),
                        minLength = 1,
                        maxLength = 2,
                    ),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        assertEquals(listOf(JsConcreteValue.String("xy")), assertIs<JsConcreteValue.Array>(inputs.single()).elements)
        fixture.assertReplayConfirmed(inputs)
    }
}
