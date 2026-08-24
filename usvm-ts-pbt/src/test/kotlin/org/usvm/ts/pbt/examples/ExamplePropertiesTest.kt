package org.usvm.ts.pbt.examples

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.fastcheck.FastCheckProjectionClient
import org.usvm.ts.pbt.fastcheck.FastCheckProjectionRequest
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExamplePropertiesTest {
    @Test
    fun `four Kotlin property shapes validate serialize and project through fast-check`() {
        assertNotNull(javaClass.getResource("/properties/examples/PropertyExamples.ts"))

        val client = FastCheckProjectionClient()

        examples.forEach { definition ->
            assertTrue(validatePropertyDefinition(definition).isValid, definition.id.value)

            val manifest = definition.toManifest()

            assertEquals(manifest, PropertyManifestJson.decode(PropertyManifestJson.encode(manifest)))

            val response = client.sample(
                FastCheckProjectionRequest(
                    seed = 42,
                    numSamples = 5,
                    domains = definition.inputs.map(PropertyInput::domain),
                ),
            )

            assertEquals(5, response.samples.size)
            assertTrue(response.samples.all { it.size == definition.inputs.size })
        }
    }

    private companion object {
        const val MODULE = "properties/examples/PropertyExamples.ts"

        val examples = listOf(
            PropertyDefinition(
                id = PropertyId("example.relational"),
                inputs = listOf(
                    PropertyInput("left", IntegerDomain()),
                    PropertyInput("right", IntegerDomain()),
                ),
                predicate = TypeScriptEntryPoint(MODULE, "isCommutative"),
            ),
            PropertyDefinition(
                id = PropertyId("example.bounded"),
                inputs = listOf(PropertyInput("value", IntegerDomain(-100, 100))),
                predicate = TypeScriptEntryPoint(MODULE, "boundedValueStaysBounded"),
            ),
            PropertyDefinition(
                id = PropertyId("example.precondition"),
                inputs = listOf(
                    PropertyInput("dividend", IntegerDomain(-100, 100)),
                    PropertyInput("divisor", IntegerDomain(-10, 10)),
                ),
                predicate = TypeScriptEntryPoint(MODULE, "divisionRoundTrip"),
                precondition = TypeScriptEntryPoint(MODULE, "nonZeroDivisor"),
            ),
            PropertyDefinition(
                id = PropertyId("example.array"),
                inputs = listOf(
                    PropertyInput("values", ArrayDomain(IntegerDomain(-5, 5), minLength = 0, maxLength = 5)),
                ),
                predicate = TypeScriptEntryPoint(MODULE, "reverseTwicePreservesValues"),
            ),
        )
    }
}
