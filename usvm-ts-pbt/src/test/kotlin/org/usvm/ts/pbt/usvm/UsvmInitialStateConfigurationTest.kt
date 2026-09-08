package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import org.usvm.util.mkRegisterStackLValue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsvmInitialStateConfigurationTest {
    @Test
    fun `initial state configuration participates in the first solver model`() {
        val source = testResourcePath("/mapping/PropertyMappingFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(listOf(file))
        val method = scene.projectClasses
            .flatMap { etsClass -> etsClass.methods }
            .single { candidate -> candidate.name == "isPositive" }
        val options = UMachineOptions(stateCollectionStrategy = StateCollectionStrategy.ALL)

        val states = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyze(
                methods = listOf(method),
                configureInitialState = { configuredMethod, state ->
                    assertEquals(method, configuredMethod)

                    with(state.ctx) {
                        val input = state.memory.read(mkRegisterStackLValue(fp64Sort, 1)).asExpr(fp64Sort)
                        state.pathConstraints += mkFpEqualExpr(input, mkFp(7.0, fp64Sort))
                    }
                },
            )
        }

        assertTrue(states.isNotEmpty())
        states.forEach { state ->
            with(state.ctx) {
                val input = state.memory.read(mkRegisterStackLValue(fp64Sort, 1)).asExpr(fp64Sort)
                val evaluated = state.models.single().eval(input)

                assertEquals(mkFp(7.0, fp64Sort), evaluated)
            }
        }
    }

    @Test
    fun `ordinary constraint pruning is not reported as an engine failure`() {
        val source = testResourcePath("/usvm/UsvmCapabilityFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(listOf(file))
        val manifest = PropertyManifest(
            propertyId = "usvm.constraint-pruning",
            inputs = listOf(
                PropertyInput(
                    name = "value",
                    domain = ArrayDomain(
                        element = IntegerDomain(min = 0, max = 0),
                        minLength = 2,
                        maxLength = 2,
                    ),
                ),
            ),
            predicate = TypeScriptEntryPoint(
                module = "UsvmCapabilityFixture.ts",
                exportName = "acceptsNumberArray",
            ),
        )
        val mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(source.parent))
        val predicate = requireNotNull(mapper.map(manifest).predicate.exactTargetOrNull())
        val options = UMachineOptions(stateCollectionStrategy = StateCollectionStrategy.ALL)

        val analysis = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(maxArraySize = 1),
        ).use { machine ->
            machine.analyzeWithMetadata(
                methods = listOf(predicate.method),
                configureInitialState = { _, state ->
                    UsvmDomainProjector().configure(
                        state = state,
                        inputs = manifest.inputs,
                        bindings = predicate.bindings.inputs,
                    )
                },
            )
        }

        assertEquals(emptyList(), analysis.states)
        assertFalse(analysis.engineFailed)
    }
}
