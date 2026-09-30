package org.usvm.ts.pbt.examples

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.FastCheckProjectionClient
import org.usvm.ts.pbt.fastcheck.FastCheckProjectionRequest
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ArrayIndexGenerator
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExamplePropertiesTest {
    @Test
    fun `declared array index relationship holds during generation and rejects invalid examples`() {
        val property = PropertyDefinition(
            id = PropertyId("example.array-index"),
            inputs = listOf(
                PropertyInput("values", ArrayDomain(IntegerDomain(min = 0, max = 2), minLength = 1, maxLength = 4)),
                PropertyInput("index", IntegerDomain(min = 0, max = 3)),
            ),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = "indexedValueIsPresent"),
            generator = ArrayIndexGenerator(id = "values.valid-index", arrayInputIndex = 0, indexInputIndex = 1),
        )
        val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))

        val run = backend.run(property, PropertyRunConfiguration(seed = 42, numRuns = 100))

        assertEquals(PropertyRunStatus.SUCCESS, run.status)
        assertFailsWith<PbtBackendException> {
            backend.run(
                property,
                PropertyRunConfiguration(
                    examples = listOf(
                        listOf(
                            JsConcreteValue.Array(listOf(JsConcreteValue.number(1.0))),
                            JsConcreteValue.number(1.0),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `four Kotlin property shapes validate serialize and project through fast-check`() {
        assertNotNull(javaClass.getResource("/properties/examples/PropertyExamples.ts"))

        val client = FastCheckProjectionClient()
        val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))

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

            val run = backend.run(definition, PropertyRunConfiguration(seed = 42, numRuns = 5))

            assertEquals(definition.id, run.propertyId)
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
