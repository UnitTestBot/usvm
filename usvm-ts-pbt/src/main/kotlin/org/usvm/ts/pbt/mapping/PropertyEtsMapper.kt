package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsScene
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.PropertyId
import java.nio.file.Path

/** Coordinates entry-point and coverage mapping for one project scene. */
class PropertyEtsMapper(
    scene: EtsScene,
    sourceRoots: List<Path>,
) {
    private val sourceLocations = SourceLocationNormalizer(sourceRoots)
    private val entryPointResolver = EtsEntryPointResolver(scene, sourceLocations)
    private val coverageMapper = EtsCoverageMapper(scene, sourceLocations)

    /** Produces a complete mapping artifact even when individual entry points or coverage locations do not map. */
    fun map(
        manifest: PropertyManifest,
        coverage: PropertyCoverageArtifact? = null,
    ): PropertyEtsMappingArtifact {
        val propertyId = PropertyId(manifest.propertyId)
        val predicate = entryPointResolver.resolve(manifest.predicate, manifest)
        val precondition = manifest.precondition?.let { entryPoint ->
            entryPointResolver.resolve(entryPoint, manifest)
        }

        return PropertyEtsMappingArtifact(
            propertyId = propertyId,
            provenance = EtsMappingProvenance(
                sourceRoots = sourceLocations.normalizedSourceRoots.map(Path::toString),
                coordinates = EtsSourceCoordinateSystem.TYPESCRIPT_UTF16_ZERO_BASED_HALF_OPEN,
                branchSuccessorOrder = EtsBranchSuccessorOrder.TRUE_FALSE,
            ),
            predicate = predicate,
            precondition = precondition,
            coverage = coverageMapper.map(propertyId, coverage),
        )
    }
}
