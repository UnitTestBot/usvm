package org.usvm.ts.pbt.usvm

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.fastcheck.FastCheckProjectionClient
import org.usvm.ts.pbt.fastcheck.FastCheckProjectionRequest
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.contains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsvmProjectionConformanceTest {
    @Test
    fun `fast-check samples satisfy the same domains used by USVM constraint tests`() {
        val domains = listOf<PropertyDomain>(
            IntegerDomain(min = -5, max = 7),
            NumberDomain(
                min = JsNumber.finite(-1.5),
                max = JsNumber.finite(2.5),
                allowNaN = false,
            ),
            OptionalDomain(IntegerDomain(min = 1, max = 3)),
            TupleDomain(listOf(IntegerDomain(min = 2, max = 4), BooleanDomain)),
            ArrayDomain(IntegerDomain(min = -1, max = 1), minLength = 1, maxLength = 3),
        )
        val response = FastCheckProjectionClient().sample(
            FastCheckProjectionRequest(
                seed = 351,
                numSamples = 50,
                domains = domains,
            ),
        )

        assertEquals(50, response.samples.size)
        response.samples.forEach { sample ->
            assertEquals(domains.size, sample.size)
            sample.zip(domains).forEach { (value, domain) ->
                assertTrue(value in domain, "$value is outside $domain")
            }
        }
    }
}
