package org.usvm.ts.calls

import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

internal class CurrentTsCallsTypeAssertionTest : CurrentTsCallsSymbolicEngineTestSupport() {
    @Test
    fun `Calls erases type assertions and replays the original runtime number`() {
        val fixture = fixture(
            source = """
                export function assertedNumber(value: number): boolean {
                  const asserted = value as unknown as string;
                  if (typeof asserted === "number" && asserted === 42) {
                    return true;
                  }
                  return false;
                }
            """.trimIndent(),
            exportName = "assertedNumber",
            inputs = listOf(PropertyInput(name = "value", domain = NumberDomain())),
            targetStatement = "return true;",
        )

        val result = fixture.search(modelIds = emptySet())

        val inputs = assertNotNull(result.inputs, result.toString())
        val value = assertIs<JsConcreteValue.Number>(inputs.single()).toDouble()
        assertEquals(42.0, value)
        fixture.assertReplayConfirmed(inputs)
    }
}
