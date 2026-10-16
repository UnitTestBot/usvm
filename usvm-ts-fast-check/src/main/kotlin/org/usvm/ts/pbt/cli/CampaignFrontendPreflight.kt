package org.usvm.ts.pbt.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.usvm.UsvmProjectionCapabilityResolver
import org.usvm.ts.pbt.usvm.UsvmProjectionOptions
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Inspects the unchanged experiment source before any treatment can influence eligibility. */
internal object CampaignFrontendPreflight {
    @Serializable
    data class Result(
        val supported: Boolean,
        val classDeclarations: Int = 0,
        val classStatements: Int = 0,
        val predicateMapping: String? = null,
        val preconditionMapping: String? = null,
        val symbolicLevel: ProjectionLevel? = null,
        val diagnostics: List<String> = emptyList(),
    )

    fun run(args: Array<String>, errors: Appendable = System.err): Int {
        require(args.size == 2) { "campaign-preflight requires a case JSON and a result destination" }
        val requestFile = Path.of(args[0]).toRealPath()
        val request = PropertyManifestJson.json.parseToJsonElement(Files.readString(requestFile)).jsonObject
        val root = requestFile.parent.resolve(request.getValue("sourceRoot").jsonPrimitive.content).toRealPath()
        val classSource = request.getValue("classSource").jsonPrimitive.content
        val className = request.getValue("className").jsonPrimitive.content
        val property = PropertyManifestJson.json.decodeFromJsonElement<PropertyDefinition>(request.getValue("property"))
        val result = try {
            inspect(root, classSource, className, property)
        } catch (error: IOException) {
            Result(supported = false, diagnostics = listOf(error.message.orEmpty()))
        } catch (error: IllegalArgumentException) {
            Result(supported = false, diagnostics = listOf(error.message.orEmpty()))
        } catch (error: IllegalStateException) {
            Result(supported = false, diagnostics = listOf(error.message.orEmpty()))
        }

        Files.writeString(Path.of(args[1]), PropertyManifestJson.json.encodeToUtf8SafeString(result))
        if (!result.supported) errors.appendLine(result.diagnostics.joinToString())
        return if (result.supported) 0 else 2
    }

    private fun inspect(root: Path, classSource: String, className: String, property: PropertyDefinition): Result {
        val generated = generateEtsIR(
            keepPartialOutputOnFailure = false,
            projectPath = root,
            isProject = true,
            loadEntrypoints = false,
            useArkAnalyzerTypeInference = 1,
            timeout = 30.seconds,
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val scene = try {
            loadEtsProjectFromIR(projectFilesPath = generated, sdkFilesPath = null)
        } finally {
            generated.toFile().deleteRecursively()
        }
        val expectedFile = root.resolve(classSource).normalize().toRealPath()
        val classes = scene.projectClasses.filter { owner ->
            val source = root.resolve(owner.signature.file.fileName).normalize()
            owner.name == className && owner.category == EtsClassCategory.CLASS && source == expectedFile
        }
        val statements = classes.sumOf { owner -> owner.methods.sumOf { it.cfg.stmts.size } }
        val manifest = property.toManifest()
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = listOf(root)).map(manifest)
        val capability = UsvmProjectionCapabilityResolver(scene).resolve(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            options = UsvmProjectionOptions(),
        )
        val exactProperty = mapping.predicate.status == EtsMappingStatus.EXACT &&
            (mapping.precondition == null || mapping.precondition?.status == EtsMappingStatus.EXACT)
        val diagnostics = capability.symbolic.diagnostics.map { it.message }.toMutableList()
        if (classes.size != 1 || statements == 0) {
            diagnostics += "The original class has ${classes.size} IR declarations and " +
                "$statements executable IR statements"
        }
        return Result(
            supported = classes.size == 1 && statements > 0 && exactProperty &&
                capability.symbolic.level != ProjectionLevel.UNSUPPORTED,
            classDeclarations = classes.size,
            classStatements = statements,
            predicateMapping = mapping.predicate.status.name,
            preconditionMapping = mapping.precondition?.status?.name,
            symbolicLevel = capability.symbolic.level,
            diagnostics = diagnostics,
        )
    }
}
