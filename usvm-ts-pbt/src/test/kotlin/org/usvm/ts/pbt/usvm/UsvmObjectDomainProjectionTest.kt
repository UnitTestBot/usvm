package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsvmObjectDomainProjectionTest {
    @Test
    fun `object candidates preserve entry fields through mutation and preconditions`() {
        val domain = ObjectDomain(mapOf("count" to IntegerDomain(min = -2, max = 2)))

        val result = search("objectMutation", domain, precondition = "positiveObject")

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status, result.toString())
        assertEquals(listOf(objectValue("count")), result.inputs)
    }

    @Test
    fun `class annotations do not grant prototype identity or missing fields`() {
        val domain = ObjectDomain(mapOf("value" to IntegerDomain(min = 2, max = 2)))

        val identity = search("plainClassIdentity", domain)
        val absent = search("absentClassField", domain)

        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, identity.status, identity.toString())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, absent.status, absent.toString())
    }

    @Test
    fun `nearest inherited field declarations retain structural inputs`() {
        val domain = ObjectDomain(mapOf("value" to IntegerDomain(min = 0, max = 2)))

        val result = search("inheritedField", domain)

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status, result.toString())
        assertEquals(listOf(objectValue("value")), result.inputs)
    }

    @Test
    fun `nested array objects recover their values from the initial snapshot`() {
        val element = ObjectDomain(mapOf("count" to IntegerDomain(min = 0, max = 2)))
        val domain = ObjectDomain(mapOf("entries" to ArrayDomain(element, minLength = 1, maxLength = 1)))

        val result = search("nestedObjects", domain)

        val expected = JsConcreteValue.Object(mapOf("entries" to JsConcreteValue.Array(listOf(objectValue("count")))))
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status, result.toString())
        assertEquals(listOf(expected), result.inputs)
    }

    @Test
    fun `optional objects and optional fields preserve their selected kind`() {
        val count = IntegerDomain(min = 0, max = 2)
        val optionalObject = OptionalDomain(ObjectDomain(mapOf("count" to count)))
        val optionalField = ObjectDomain(mapOf("count" to OptionalDomain(count)))

        val objectResult = search("optionalObject", optionalObject)
        val fieldResult = search("optionalField", optionalField)

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, objectResult.status, objectResult.toString())
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, fieldResult.status, fieldResult.toString())
        assertEquals(listOf(objectValue("count")), objectResult.inputs)
        assertEquals(listOf(objectValue("count")), fieldResult.inputs)
    }

    @Test
    fun `an any annotation does not discard a declared object domain`() {
        val domain = ObjectDomain(mapOf("count" to IntegerDomain(min = 0, max = 2)))

        val result = search("untypedObject", domain)

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status, result.toString())
        assertEquals(listOf(objectValue("count")), result.inputs)
    }

    @Test
    fun `incompatible supplied fields retain a precise capability diagnostic`() {
        val domain = ObjectDomain(mapOf("count" to IntegerDomain(min = 0, max = 2)))

        val result = search("acceptsBooleanField", domain)

        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, result.status)
        assertNull(result.inputs)
        assertTrue(result.diagnostics.any { it.path == "inputs[0].domain.fields.count" }, result.toString())
    }

    @Test
    fun `optional object type matching preserves the configured collection bound`() {
        val fields = mapOf("entries" to ArrayDomain(IntegerDomain(), maxLength = 11))
        val domain = OptionalDomain(value = ObjectDomain(fields = fields))
        val manifest = PropertyManifest(
            propertyId = "objects.optional-capacity",
            inputs = listOf(PropertyInput(name = "value", domain = domain)),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = "optionalCollection"),
        )
        val mapping = mapper.map(manifest)
        val resolver = UsvmProjectionCapabilityResolver(scene)
        val concrete = ProjectionCapability(level = ProjectionLevel.EXACT)

        val admitted = resolver.resolve(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = concrete,
            options = UsvmProjectionOptions(maxSymbolicCollectionLength = 12),
        )
        val bounded = resolver.resolve(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = concrete,
            options = UsvmProjectionOptions(maxSymbolicCollectionLength = 10),
        )

        assertEquals(ProjectionLevel.EXACT, admitted.symbolic.level)
        assertEquals(ProjectionLevel.UNSUPPORTED, bounded.symbolic.level)
        assertEquals("inputs[0].domain.value.fields.entries", bounded.symbolic.diagnostics.single().path)
    }

    private fun objectValue(name: String) = JsConcreteValue.Object(mapOf(name to JsConcreteValue.number(2.0)))

    private fun search(
        predicate: String,
        domain: PropertyDomain,
        precondition: String? = null,
    ): UsvmPropertySearchResult {
        val manifest = PropertyManifest(
            propertyId = "objects.$predicate",
            inputs = listOf(PropertyInput(name = "value", domain = domain)),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = predicate),
            precondition = precondition?.let { TypeScriptEntryPoint(module = MODULE, exportName = it) },
        )

        return searcher.search(
            manifest = manifest,
            mapping = mapper.map(manifest),
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
        )
    }

    companion object {
        private const val MODULE = "UsvmObjectProjectionFixture.ts"
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var scene: EtsScene
        private lateinit var searcher: UsvmPropertySearcher

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/usvm/$MODULE")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            scene = EtsScene(listOf(file))

            mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(source.parent))
            searcher = UsvmPropertySearcher(scene)
        }
    }
}
