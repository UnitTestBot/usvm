package org.usvm.ts.pbt.model

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyDomainTest {
    @Test
    fun `integer membership matches values produced by fast-check integer`() {
        val domain = IntegerDomain(min = -1, max = 1)

        assertTrue(JsConcreteValue.number(-1.0) in domain)
        assertTrue(JsConcreteValue.number(0.0) in domain)
        assertTrue(JsConcreteValue.number(1.0) in domain)
        assertFalse(JsConcreteValue.number(-0.0) in domain)
        assertFalse(JsConcreteValue.number(0.5) in domain)
        assertFalse(JsConcreteValue.number(Double.NaN) in domain)
        assertFalse(JsConcreteValue.number(Double.POSITIVE_INFINITY) in domain)
    }

    @Test
    fun `number membership preserves binary64 special values and inclusive bounds`() {
        val zero = NumberDomain(
            min = JsNumber.finite(0.0),
            max = JsNumber.finite(0.0),
            allowNaN = false,
        )
        val unbounded = NumberDomain()

        assertTrue(JsConcreteValue.number(0.0) in zero)
        assertTrue(JsConcreteValue.number(-0.0) in zero)
        assertFalse(JsConcreteValue.number(1.0) in zero)
        assertTrue(JsConcreteValue.number(Double.NaN) in unbounded)
        assertTrue(JsConcreteValue.number(Double.NEGATIVE_INFINITY) in unbounded)
        assertTrue(JsConcreteValue.number(Double.POSITIVE_INFINITY) in unbounded)
    }

    @Test
    fun `invalid number encodings do not belong to numeric domains`() {
        val invalidNumber = JsConcreteValue.Number(
            number = JsNumber(value = JsNumberKind.FINITE, bits = "invalid"),
        )

        assertFalse(invalidNumber in IntegerDomain())
        assertFalse(invalidNumber in NumberDomain())
    }
}
