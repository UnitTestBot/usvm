package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsScene
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsOptions
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.dsl.CompiledDslProperty
import org.usvm.ts.pbt.inference.ScalarRelation
import org.usvm.ts.pbt.inference.ScalarRelationInference
import org.usvm.ts.pbt.inference.ScalarRelationTemplate
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsMappingResult
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue

/** An auxiliary candidate at one compiler-owned store; native replay decides the complete original outcome. */
data class UsvmDslNumericLocalRefutation(
    val inference: ScalarRelationInference,
    val binding: EtsMappingResult<UsvmDslNumericLocalBinding>,
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

/** Searches domain AND original admission AND NOT(relation) immediately after the selected original store. */
class UsvmDslNumericLocalRefuter(
    scene: EtsScene,
    machineOptions: UMachineOptions = UMachineOptions(solverType = SolverType.YICES),
    tsOptions: TsOptions = TsOptions(),
    projectionOptions: UsvmProjectionOptions = UsvmProjectionOptions(),
) {
    private val search = UsvmRelationRefutationSearch(scene, machineOptions, tsOptions, projectionOptions)

    fun refute(
        compiled: CompiledDslProperty,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
        inference: ScalarRelationInference,
        relation: ScalarRelation,
    ): UsvmDslNumericLocalRefutation {
        val manifest = compiled.definition.toManifest()
        val binding = bindDslNumericLocalRelation(compiled, mapping, inference, relation)
        val capability = search.capability(manifest, mapping, concreteCapability)
        val unsupported = binding.status != EtsMappingStatus.EXACT ||
            capability.symbolic.level == ProjectionLevel.UNSUPPORTED
        if (unsupported) {
            return UsvmDslNumericLocalRefutation(
                inference = inference,
                binding = binding,
                status = UsvmRelationRefutationStatus.UNSUPPORTED,
                inputs = null,
                capability = capability,
                diagnostics = capability.symbolic.diagnostics,
            )
        }
        val observer = NumericLocalRefutationObserver(binding.targets.single())
        val result = search.search(manifest, mapping, capability, observer)

        return UsvmDslNumericLocalRefutation(
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

private class NumericLocalRefutationObserver(
    private val binding: UsvmDslNumericLocalBinding,
) : UsvmRelationRefutationObserver() {
    override fun onAssignmentCompleted(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsAssignStmt,
        scope: TsStepScope,
    ) {
        val selectedStore = stmt === binding.assignment && isAdmittedRoot(scope)
        if (!selectedStore || reached != null) return

        admittedPointReached = true
        val value = binding.local.accept(simpleValueResolver)
        val candidate = scope.calcOnState { clone() }
        with(candidate.ctx) {
            if (value == null || value.sort != fp64Sort) {
                pointDiagnostics += CapabilityDiagnostic(
                    code = "usvm.dsl-local.sort-unsupported",
                    message = "The selected numeric local has an unsupported symbolic representation",
                    path = binding.compiler.localName,
                )
            } else {
                val original = projection.inputs[binding.inputIndex].value.asExpr(fp64Sort)
                val stored = value.asExpr(fp64Sort)
                val left = if (binding.resultIsLeft) stored else original
                val right = if (binding.resultIsLeft) original else stored
                val hypothesis = when (binding.relation.template) {
                    ScalarRelationTemplate.STRICT_EQUAL -> mkFpEqualExpr(left, right)
                    ScalarRelationTemplate.LESS_OR_EQUAL -> mkFpLessOrEqualExpr(left, right)
                }
                candidate.pathConstraints += hypothesis.not()
                consider(candidate)
            }

            // The interpreter still advances this completed assignment before collecting its step result.
            // A false path ends this auxiliary prefix there; the complete suffix is checked natively.
            scope.doWithState { pathConstraints += falseExpr }
        }
    }
}
