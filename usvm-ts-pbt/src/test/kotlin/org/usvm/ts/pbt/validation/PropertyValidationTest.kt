package org.usvm.ts.pbt.validation

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.PROPERTY_MANIFEST_SCHEMA_VERSION
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.JsNumberKind
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyValidationTest {
    @Test
    fun `validation reports independent structural errors in deterministic order`() {
        val definition = PropertyDefinition(
            id = PropertyId.unchecked(" bad id "),
            inputs = listOf(
                PropertyInput("value", IntegerDomain(10, -10)),
                PropertyInput("value", StringDomain(-1, 0)),
            ),
            predicate = TypeScriptEntryPoint("../escape.ts", "not-valid-name"),
        )

        val result = validatePropertyDefinition(definition)

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                "domain.integer.bounds",
                "input.name.duplicate",
                "domain.string.length",
                "entrypoint.export.invalid",
                "entrypoint.module.invalid",
                "property.id.invalid",
            ),
            result.diagnostics.map { it.code },
        )
    }

    @Test
    fun `property ID rejects invalid canonical text at construction`() {
        assertFailsWith<IllegalArgumentException> { PropertyId(" bad id ") }
    }

    @Test
    fun `optional domain accepts only null or undefined as nil`() {
        val definition = validDefinition(
            OptionalDomain(IntegerDomain(), JsConcreteValue.String("none")),
        )

        assertEquals(
            listOf("domain.optional.nil"),
            validatePropertyDefinition(definition).diagnostics.map { it.code },
        )
    }

    @Test
    fun `constant domain rejects composite JavaScript values`() {
        val definition = validDefinition(ConstantDomain(JsConcreteValue.Array(listOf(JsConcreteValue.Null))))

        assertEquals(
            listOf("domain.constant.unsupported"),
            validatePropertyDefinition(definition).diagnostics.map { it.code },
        )
    }

    @Test
    fun `invalid number encodings are diagnosed without comparing the bounds`() {
        val invalidMinimum = JsNumber(JsNumberKind.FINITE, bits = "invalid")
        val definition = validDefinition(
            NumberDomain(
                min = invalidMinimum,
                max = JsNumber.finite(1.0),
                allowNaN = false,
            ),
        )

        assertEquals(
            listOf("js-number.encoding.invalid"),
            validatePropertyDefinition(definition).diagnostics.map { it.code },
        )
    }

    @Test
    fun `manifest validation rejects unknown schema version`() {
        val manifest = PropertyManifest(
            schemaVersion = PROPERTY_MANIFEST_SCHEMA_VERSION + 1,
            propertyId = "valid.id",
            inputs = listOf(PropertyInput("value", IntegerDomain())),
            predicate = TypeScriptEntryPoint("properties/value.ts", "holds"),
        )

        assertEquals(
            listOf("manifest.schema.unsupported"),
            validatePropertyManifest(manifest).diagnostics.map { it.code },
        )
    }

    @Test
    fun `valid definition has no diagnostics`() {
        assertTrue(validatePropertyDefinition(validDefinition(IntegerDomain(-5, 5))).isValid)
    }

    private fun validDefinition(domain: PropertyDomain) = PropertyDefinition(
        id = PropertyId("valid.id"),
        inputs = listOf(PropertyInput("value", domain)),
        predicate = TypeScriptEntryPoint("properties/value.ts", "holds"),
    )
}
