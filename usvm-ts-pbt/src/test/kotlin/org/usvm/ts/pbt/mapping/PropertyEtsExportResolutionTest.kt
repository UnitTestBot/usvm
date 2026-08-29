package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import java.nio.file.Path
import kotlin.test.assertEquals

class PropertyEtsExportResolutionTest {
    @Test
    fun `direct function export ignores same-named class methods`() {
        val source = testResourcePath("/mapping/exports/DirectExportFixture.ts")
        val mapper = mapper(source)

        val artifact = mapper.map(manifest(module = source.fileName.toString(), exportName = "predicate"))

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val method = artifact.predicate.targets.single().method
        assertEquals("predicate", method.name)
        assertEquals("%dflt", method.signature.enclosingClass.name)
    }

    @Test
    fun `namespace star export is not a transparent named re-export`() {
        val entrySource = testResourcePath("/mapping/exports/NamespaceEntry.ts")
        val predicateSource = testResourcePath("/mapping/exports/Predicate.ts")
        val mapper = mapper(entrySource, predicateSource)

        val artifact = mapper.map(manifest(module = entrySource.fileName.toString(), exportName = "corePredicate"))

        assertEquals(EtsMappingStatus.UNMAPPED, artifact.predicate.status)
        assertEquals(emptyList(), artifact.predicate.targets)
    }

    @Test
    fun `explicit named re-export takes precedence over bare star export`() {
        val sourceDirectory = testResourcePath("/mapping/exports")
        val sources = listOf("ExplicitPrecedenceEntry.ts", "Predicate.ts", "StarPredicate.ts")
            .map(sourceDirectory::resolve)
        val mapper = mapper(*sources.toTypedArray())

        val artifact = mapper.map(manifest(module = "ExplicitPrecedenceEntry.ts", exportName = "predicate"))
        val target = artifact.predicate.targets.single()

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        assertEquals("corePredicate", target.method.name)
    }

    @Test
    fun `type-only declaration does not mask a bare star value export`() {
        val sourceDirectory = testResourcePath("/mapping/exports")
        val sources = listOf("TypeOnlyPrecedenceEntry.ts", "StarPredicate.ts")
            .map(sourceDirectory::resolve)
        val mapper = mapper(*sources.toTypedArray())

        val artifact = mapper.map(manifest(module = "TypeOnlyPrecedenceEntry.ts", exportName = "predicate"))

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val target = artifact.predicate.targets.single()
        assertEquals("predicate", target.method.name)
    }

    @Test
    fun `bare star export does not forward the default export`() {
        val entrySource = testResourcePath("/mapping/exports/StarDefaultEntry.ts")
        val predicateSource = testResourcePath("/mapping/exports/DefaultPredicate.ts")
        val mapper = mapper(entrySource, predicateSource)

        val artifact = mapper.map(manifest(module = entrySource.fileName.toString(), exportName = "default"))

        assertEquals(EtsMappingStatus.UNMAPPED, artifact.predicate.status)
        assertEquals(emptyList(), artifact.predicate.targets)
    }

    @Test
    fun `duplicate re-export paths resolve one EtsIR method exactly`() {
        val sourceDirectory = testResourcePath("/mapping/exports")
        val sources = listOf("DiamondEntry.ts", "Left.ts", "Right.ts", "Predicate.ts")
            .map(sourceDirectory::resolve)
        val mapper = mapper(*sources.toTypedArray())

        val artifact = mapper.map(manifest(module = "DiamondEntry.ts", exportName = "predicate"))

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val targetMethod = artifact.predicate.targets.single().method
        assertEquals("corePredicate", targetMethod.name)
    }

    private fun mapper(vararg sources: Path): PropertyEtsMapper {
        val files = sources.map { source ->
            loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        }

        return PropertyEtsMapper(
            scene = EtsScene(files),
            sourceRoots = listOf(sources.first().parent),
        )
    }

    private fun manifest(module: String, exportName: String): PropertyManifest = PropertyManifest(
        propertyId = "mapping.export-resolution",
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
        predicate = TypeScriptEntryPoint(module = module, exportName = exportName),
    )
}
