package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.inference.ScalarRelation
import org.usvm.ts.pbt.inference.ScalarRelationInference
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsMappingResult
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue

/** Source bridge for the entry-only name; it does not retain the old enum's JVM binary identity. */
typealias UsvmEntryRefutationStatus = UsvmRelationRefutationStatus

/** Reaches an admitted original input that refutes a sample; the original oracle still needs native execution. */
data class UsvmEntryRelationRefutation(
    val inference: ScalarRelationInference,
    val binding: EtsMappingResult<UsvmEntryRelationBinding>,
    val status: UsvmRelationRefutationStatus,
    val inputs: List<JsConcreteValue>?,
    val capability: UsvmPropertyProjectionCapability,
    val diagnostics: List<CapabilityDiagnostic>,
    val timedOut: Boolean = false,
) {
    init {
        require(status != UsvmRelationRefutationStatus.REFUTATION_REACHED || inputs != null)
        require(inputs == null || binding.status == EtsMappingStatus.EXACT)
    }
}

/**
 * Searches domain AND original admission AND NOT(entry relation), without negating the original oracle.
 * The standalone machine uses the supplied timeout; the campaign owns its shared deadline and cancellation.
 */
class UsvmEntryRelationRefuter(
    scene: EtsScene,
    machineOptions: UMachineOptions = UMachineOptions(solverType = SolverType.YICES),
    tsOptions: TsOptions = TsOptions(),
    projectionOptions: UsvmProjectionOptions = UsvmProjectionOptions(),
) {
    private val search = UsvmRelationRefutationSearch(scene, machineOptions, tsOptions, projectionOptions)

    fun refute(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
        inference: ScalarRelationInference,
        relation: ScalarRelation,
    ): UsvmEntryRelationRefutation {
        val binding = bindEntryRelation(manifest, mapping, inference, relation)
        val capability = search.capability(manifest, mapping, concreteCapability)
        val unsupported = binding.status != EtsMappingStatus.EXACT ||
            capability.symbolic.level == ProjectionLevel.UNSUPPORTED
        if (unsupported) {
            return UsvmEntryRelationRefutation(
                inference = inference,
                binding = binding,
                status = UsvmRelationRefutationStatus.UNSUPPORTED,
                inputs = null,
                capability = capability,
                diagnostics = capability.symbolic.diagnostics,
            )
        }
        val predicate = requireNotNull(mapping.predicate.exactTargetOrNull())
        val observer = EntryRefutationObserver(predicate.method, binding.targets.single())
        val result = search.search(manifest, mapping, capability, observer)

        return UsvmEntryRelationRefutation(
            inference = inference,
            binding = binding,
            status = result.status,
            inputs = result.inputs,
            capability = result.capability,
            diagnostics = result.diagnostics,
            timedOut = result.timedOut,
        )
    }
}

private class EntryRefutationObserver(
    private val predicate: EtsMethod,
    private val binding: UsvmEntryRelationBinding,
) : UsvmRelationRefutationObserver() {
    override fun configureProjection(state: TsState, projection: UsvmDeclaredDomainProjection) {
        super.configureProjection(state, projection)
        state.pathConstraints += binding.negatedRelation(state, projection)
    }

    override fun onStatementEntered(stmt: EtsStmt, scope: TsStepScope) {
        val firstEntryStatement = stmt === predicate.cfg.instructions.first()
        val admittedEntry = isAdmittedRoot(scope)
        if (reached != null || !firstEntryStatement || !admittedEntry) return

        admittedPointReached = true
        consider(scope.calcOnState { clone() })
    }
}
