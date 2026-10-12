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
            val undefinedTarget = targetValue == JsConcreteValue.Undefined
            val condition = if (undefinedTarget) "value === undefined" else "value === 7"
            val exportName = if (undefinedTarget) "optionalUndefined" else "optionalNumber"
            val fixture = fixture(
                source = """
                    export function $exportName(value: number | undefined): boolean {
                      if ($condition) {
                        return true;
                      }
                      return false;
                    }
                """.trimIndent(),
                exportName = exportName,
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

    @Test
    fun `nested numeric arrays preserve dimensions and replay actual source`() {
        val fixture = fixture(
            source = """
                export function matrix(values: number[][]): boolean {
                  if (values.length === 1 && values[0].length === 1 && values[0][0] === 7) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "matrix",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(
                        element = ArrayDomain(element = IntegerDomain(min = 0, max = 10), minLength = 1, maxLength = 2),
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
        val outer = assertIs<JsConcreteValue.Array>(inputs.single())
        val inner = assertIs<JsConcreteValue.Array>(outer.elements.single())
        assertEquals(listOf(JsConcreteValue.number(7.0)), inner.elements)
        fixture.assertReplayConfirmed(inputs)
    }

    @Test
    fun `optional nested arrays register every dense region after all inputs are initialized`() {
        val rows = ArrayDomain(element = IntegerDomain(min = 0, max = 0), minLength = 1, maxLength = 2)
        val matrices = ArrayDomain(element = rows, minLength = 1, maxLength = 2)
        val fixture = fixture(
            source = """
                export function optionalMatrices(first: number[][] | undefined, second: number[][]): boolean {
                  if (first !== undefined && first[0].includes(0) && second[0].includes(0)) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "optionalMatrices",
            inputs = listOf(
                PropertyInput(name = "first", domain = OptionalDomain(matrices)),
                PropertyInput(name = "second", domain = matrices),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = setOf("ts.array.includes", "ts.math.floor"))

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        fixture.assertReplayConfirmed(assertNotNull(result.inputs, result.toString()))
    }

    @Test
    fun `nested domains reject a missing source array dimension`() {
        val fixture = fixture(
            source = """
                export function flat(values: number[]): boolean {
                  if (values.length === 1) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "flat",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(element = ArrayDomain(element = IntegerDomain())),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.preflight()

        assertEquals(CallsSymbolicPreflightStatus.UNSUPPORTED, result.status, result.toString())
        assertEquals(CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED, result.reasonCode)
        assertTrue(result.diagnostic.orEmpty().contains("inputs.values[]"))
    }


    @Test
    fun `optional array slots preserve independent numeric and undefined values`() {
        val fixture = fixture(
            source = """
                export function mixedSlots(values: (number | undefined)[]): boolean {
                  if (values.length === 2 && values[0] === 7 && values[1] === undefined) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "mixedSlots",
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(
                        element = OptionalDomain(IntegerDomain(min = 0, max = 10)),
                        minLength = 2,
                        maxLength = 2,
                    ),
                ),
            ),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        assertEquals(CallsSymbolicStatus.REACHED, result.status, result.toString())
        val inputs = assertNotNull(result.inputs, result.toString())
        assertEquals(
            listOf(JsConcreteValue.number(7.0), JsConcreteValue.Undefined),
            assertIs<JsConcreteValue.Array>(inputs.single()).elements,
        )
        fixture.assertReplayConfirmed(inputs)
    }
}
