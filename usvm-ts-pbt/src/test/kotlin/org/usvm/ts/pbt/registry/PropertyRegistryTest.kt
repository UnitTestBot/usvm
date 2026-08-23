package org.usvm.ts.pbt.registry

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.validation.InvalidPropertyDefinitionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PropertyRegistryTest {
    @Test
    fun `registry validates definitions before exposing them`() {
        val invalid = property(id = "invalid.property").copy(inputs = emptyList())

        assertFailsWith<InvalidPropertyDefinitionException> {
            PropertyRegistry(listOf(invalid))
        }
    }

    @Test
    fun `registry preserves property order and selects by id`() {
        val second = property(id = "second")
        val first = property(id = "first")

        val registry = PropertyRegistry(listOf(second, first))

        assertEquals(listOf(second, first), registry.properties)
        assertEquals(first, registry[PropertyId("first")])
    }

    @Test
    fun `duplicate property ids report every conflicting position`() {
        val error = assertFailsWith<DuplicatePropertyIdException> {
            PropertyRegistry(
                listOf(
                    property(id = "same"),
                    property(id = "different"),
                    property(id = "same"),
                ),
            )
        }

        assertEquals(PropertyId("same"), error.propertyId)
        assertEquals(listOf(0, 2), error.positions)
    }

    @Test
    fun `unknown property id produces an actionable typed error`() {
        val registry = PropertyRegistry(listOf(property(id = "known")))

        val error = assertFailsWith<UnknownPropertyIdException> {
            registry[PropertyId("missing")]
        }

        assertEquals(PropertyId("missing"), error.propertyId)
        assertEquals(listOf(PropertyId("known")), error.availablePropertyIds)
    }

    @Test
    fun `combining registries rejects ids duplicated across registries`() {
        val first = PropertyRegistry(listOf(property(id = "shared")))
        val second = PropertyRegistry(listOf(property(id = "shared")))

        assertFailsWith<DuplicatePropertyIdException> {
            PropertyRegistry.combine(listOf(first, second))
        }
    }

    private fun property(id: String) = PropertyDefinition(
        id = PropertyId(id),
        inputs = listOf(PropertyInput(name = "value", domain = BooleanDomain)),
        predicate = TypeScriptEntryPoint(
            module = "properties/example.ts",
            exportName = "predicate",
        ),
    )
}
