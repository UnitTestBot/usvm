package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
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
import kotlin.test.assertTrue

class PropertyEtsExportResolutionTest {
    @Test
    fun `named default declaration resolves only through the default export name`() {
        val source = testResourcePath("/mapping/exports/NamedDefaultDeclaration.ts")
        val mapper = mapper(source)

        val defaultArtifact = mapper.map(manifest(module = source.fileName.toString(), exportName = "default"))
        val sourceNameArtifact = mapper.map(manifest(module = source.fileName.toString(), exportName = "namedDefault"))

        assertEquals(EtsMappingStatus.EXACT, defaultArtifact.predicate.status)
        val defaultMethod = defaultArtifact.predicate.targets.single().method
        assertEquals("namedDefault", defaultMethod.name)
        assertEquals(EtsMappingStatus.UNMAPPED, sourceNameArtifact.predicate.status)
        assertEquals(emptyList(), sourceNameArtifact.predicate.targets)
    }

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
    fun `type-only named export does not mask a bare star value export`() {
        val sourceDirectory = testResourcePath("/mapping/exports")
        val sources = listOf("TypeOnlyPrecedenceEntry.ts", "TypeOnlyPredicate.ts", "StarPredicate.ts")
            .map(sourceDirectory::resolve)
        val mapper = mapper(*sources.toTypedArray())

        val artifact = mapper.map(manifest(module = "TypeOnlyPrecedenceEntry.ts", exportName = "predicate"))

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val target = artifact.predicate.targets.single()
        val enclosingClass = target.method.signature.enclosingClass
        val targetFileName = enclosingClass.file.fileName
        assertEquals("predicate", target.method.name)
        assertTrue(targetFileName.endsWith("StarPredicate.ts"))
    }

    @Test
    fun `type-only star export does not add a runtime candidate`() {
        val sourceDirectory = testResourcePath("/mapping/exports")
        val sources = listOf("TypeOnlyStarEntry.ts", "TypeOnlyPredicate.ts", "StarPredicate.ts")
            .map(sourceDirectory::resolve)
        val mapper = mapper(*sources.toTypedArray())

        val artifact = mapper.map(manifest(module = "TypeOnlyStarEntry.ts", exportName = "predicate"))

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val target = artifact.predicate.targets.single()
        val enclosingClass = target.method.signature.enclosingClass
        val targetFileName = enclosingClass.file.fileName
        assertTrue(targetFileName.endsWith("StarPredicate.ts"))
    }

    @Test
    fun `exported arrow local resolves through its lifted method`() {
        val source = testResourcePath("/mapping/exports/CallableLocalFixture.ts")
        val mapper = mapper(source)

        val artifact = mapper.map(manifest(module = source.fileName.toString(), exportName = "arrowPredicate"))

        assertEquals(EtsMappingStatus.EXACT, artifact.predicate.status)
        val method = artifact.predicate.targets.single().method
        assertTrue(method.name.startsWith("%AM"))
        assertEquals(listOf("value"), method.parameters.map { parameter -> parameter.name })
    }

    @Test
    fun `local function expression alias routes only through the export name`() {
        val source = testResourcePath("/mapping/exports/CallableLocalFixture.ts")
        val mapper = mapper(source)

        val aliasArtifact = mapper.map(
            manifest(module = source.fileName.toString(), exportName = "aliasedPredicate"),
        )
        val localNameArtifact = mapper.map(
            manifest(module = source.fileName.toString(), exportName = "functionPredicate"),
        )

        assertEquals(EtsMappingStatus.EXACT, aliasArtifact.predicate.status)
        val aliasMethod = aliasArtifact.predicate.targets.single().method
        assertTrue(aliasMethod.name.startsWith("%AM"))
        assertEquals(EtsMappingStatus.UNMAPPED, localNameArtifact.predicate.status)
        assertEquals(emptyList(), localNameArtifact.predicate.targets)
    }

    @Test
    fun `non-callable exported local remains unmapped`() {
        val source = testResourcePath("/mapping/exports/CallableLocalFixture.ts")
        val mapper = mapper(source)

        val artifact = mapper.map(manifest(module = source.fileName.toString(), exportName = "nonCallable"))

        assertEquals(EtsMappingStatus.UNMAPPED, artifact.predicate.status)
        assertEquals(emptyList(), artifact.predicate.targets)
    }

    @Test
    fun `multiple lifted assignments for one export remain ambiguous`() {
        val source = testResourcePath("/mapping/exports/CallableLocalFixture.ts")
        val mapper = mapper(source)

        val artifact = mapper.map(manifest(module = source.fileName.toString(), exportName = "reassignedPredicate"))

        assertEquals(EtsMappingStatus.AMBIGUOUS, artifact.predicate.status)
        assertEquals(2, artifact.predicate.targets.size)
        assertTrue(artifact.predicate.targets.all { target -> target.method.name.startsWith("%AM") })
        assertEquals("mapping.entry-point.ambiguous", artifact.predicate.diagnostics.single().code)
    }

    @Test
    fun `callable local followed by a non-callable assignment remains ambiguous`() {
        val source = testResourcePath("/mapping/exports/CallableLocalFixture.ts")
        val mapper = mapper(source)

        val artifact = mapper.map(manifest(module = source.fileName.toString(), exportName = "callableThenValue"))
        val target = artifact.predicate.targets.single()

        assertEquals(EtsMappingStatus.AMBIGUOUS, artifact.predicate.status)
        assertEquals(1, artifact.predicate.targets.size)
        assertTrue(target.method.name.startsWith("%AM"))
        assertEquals("mapping.entry-point.ambiguous", artifact.predicate.diagnostics.single().code)
    }

    @Test
    fun `aliased callable with repeated links to one lifted method remains ambiguous`() {
        val source = testResourcePath("/mapping/exports/CallableLocalFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val defaultClass = file.classes.single { etsClass -> etsClass.name == DEFAULT_ARK_CLASS_NAME }
        val defaultMethod = defaultClass.methods.single { method -> method.name == DEFAULT_ARK_METHOD_NAME }
        val linkedSignatures = defaultMethod.cfg.stmts
            .filterIsInstance<EtsAssignStmt>()
            .mapNotNull { assignment ->
                val field = assignment.lhv as? EtsStaticFieldRef ?: return@mapNotNull null
                if (field.field.name != "multiplyLinkedPredicate") return@mapNotNull null

                val local = assignment.rhv as? EtsLocal ?: return@mapNotNull null
                val functionType = local.type as? EtsFunctionType ?: return@mapNotNull null

                functionType.signature
            }
        val mapper = PropertyEtsMapper(
            scene = EtsScene(listOf(file)),
            sourceRoots = listOf(source.parent),
        )

        val aliasArtifact = mapper.map(
            manifest(module = source.fileName.toString(), exportName = "aliasedMultiplyLinkedPredicate"),
        )
        val localNameArtifact = mapper.map(
            manifest(module = source.fileName.toString(), exportName = "multiplyLinkedPredicate"),
        )

        assertEquals(2, linkedSignatures.size)
        assertEquals(1, linkedSignatures.distinct().size)
        assertEquals(EtsMappingStatus.AMBIGUOUS, aliasArtifact.predicate.status)
        val target = aliasArtifact.predicate.targets.single()
        assertEquals(linkedSignatures.distinct().single(), target.method.signature)
        assertTrue(target.method.name.startsWith("%AM"))
        val diagnostic = aliasArtifact.predicate.diagnostics.single()
        assertEquals("mapping.entry-point.ambiguous", diagnostic.code)
        assertEquals(
            "Several EtsIR methods, source candidates, or export links match " +
                "CallableLocalFixture.ts#aliasedMultiplyLinkedPredicate",
            diagnostic.message,
        )
        assertEquals(EtsMappingStatus.UNMAPPED, localNameArtifact.predicate.status)
        assertEquals(emptyList(), localNameArtifact.predicate.targets)
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

    @Test
    fun `re-export with multiple module files stays ambiguous when only one exports the target`() {
        val sourceDirectory = testResourcePath("/mapping/exports/ambiguous-reexport")
        val sources = listOf("Entry.ts", "Foo.ts", "Foo/index.ts").map(sourceDirectory::resolve)
        val mapper = mapper(*sources.toTypedArray())

        val artifact = mapper.map(manifest(module = "Entry.ts", exportName = "predicate"))
        val target = artifact.predicate.targets.single()

        assertEquals(EtsMappingStatus.AMBIGUOUS, artifact.predicate.status)
        assertEquals(1, artifact.predicate.targets.size)
        assertEquals("predicate", target.method.name)
        assertEquals("mapping.entry-point.ambiguous", artifact.predicate.diagnostics.single().code)
    }

    private fun mapper(vararg sources: Path): PropertyEtsMapper {
        val sourceRoot = sources.first().parent
        val files = sources.map { source ->
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)

            EtsFile(
                signature = file.signature.copy(fileName = sourceRoot.relativize(source).toString()),
                classes = file.classes,
                namespaces = file.namespaces,
                importInfos = file.importInfos,
                exportInfos = file.exportInfos,
            )
        }

        return PropertyEtsMapper(
            scene = EtsScene(files),
            sourceRoots = listOf(sourceRoot),
        )
    }

    private fun manifest(module: String, exportName: String): PropertyManifest = PropertyManifest(
        propertyId = "mapping.export-resolution",
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain())),
        predicate = TypeScriptEntryPoint(module = module, exportName = exportName),
    )
}
