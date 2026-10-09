package org.usvm.samples.operators

import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.eq

class InOperatorFieldCollisionTest : PropertyTestRunner("/samples/operators/InOperatorFieldCollision.ts") {
    @Test
    fun `own fields retain their actual sort despite unrelated declarations`() {
        val methods = listOf(
            "numericFieldWithStringCollision", "writtenStringField", "declaredStringField",
            "run", "declaredProperty", "reassignedProperty",
        )

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "Probe")

            discoverProperties<TsTestValue.TsNumber>(
                method = method,
                { result -> result eq 7 },
                invariants = arrayOf({ result -> result eq 7 }),
            )
        }
    }
}
