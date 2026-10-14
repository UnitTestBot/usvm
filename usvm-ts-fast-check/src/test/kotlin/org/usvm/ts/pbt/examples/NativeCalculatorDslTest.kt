package org.usvm.ts.pbt.examples

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.dsl.TsPropertyTest
import org.usvm.ts.pbt.dsl.equalTo
import org.usvm.ts.pbt.dsl.invoke
import org.usvm.ts.pbt.dsl.plus
import org.usvm.ts.pbt.testResourcesRoot

class NativeCalculatorDslTest : TsPropertyTest(
    sourceRoot = testResourcesRoot().resolve("properties/dsl").toString(),
) {
    @Test
    fun `addition properties`() = property {
        val sum = function<(Double, Double) -> Double>(module = "calculator.ts", exportName = "add")
        val a by int(min = -100, max = 100)
        val b by int(min = -100, max = 100)

        check(name = "addition") { sum(a, b) equalTo (a + b) }
        check(name = "commutativity") { sum(a, b) equalTo sum(b, a) }
    }
}
