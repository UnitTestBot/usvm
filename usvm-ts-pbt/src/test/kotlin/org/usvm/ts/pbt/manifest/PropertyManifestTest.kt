package org.usvm.ts.pbt.manifest

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PropertyManifestTest {
    @Test
    fun `manifest round trip contains only engine neutral property data`() {
        val definition = PropertyDefinition(
            id = PropertyId("math.commutative"),
            inputs = listOf(
                PropertyInput("left", IntegerDomain(-10, 10)),
                PropertyInput("right", IntegerDomain(-10, 10)),
            ),
            predicate = TypeScriptEntryPoint("properties/math.ts", "isCommutative"),
        )

        val manifest = definition.toManifest()
        val encoded = PropertyManifestJson.encode(manifest)

        assertEquals(manifest, PropertyManifestJson.decode(encoded))
        assertFalse("fast-check" in encoded)
        assertFalse("backend" in encoded)
        assertFalse("seed" in encoded)
    }

    @Test
    fun `manifest serializes resolved integer bounds`() {
        val definition = PropertyDefinition(
            id = PropertyId("integer.defaults"),
            inputs = listOf(PropertyInput("value", IntegerDomain())),
            predicate = TypeScriptEntryPoint("properties/integer.ts", "holds"),
        )

        val encoded = PropertyManifestJson.encode(definition.toManifest())

        assertEquals(
            """{"propertyId":"integer.defaults","inputs":[""" +
                """{"name":"value","domain":{"kind":"integer","min":-2147483648,"max":2147483647}}],""" +
                """"predicate":{"module":"properties/integer.ts","exportName":"holds","executionKind":"sync"}}""",
            encoded,
        )
    }
}
