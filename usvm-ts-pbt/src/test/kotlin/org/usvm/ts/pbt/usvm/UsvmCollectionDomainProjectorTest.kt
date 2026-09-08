package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
import org.usvm.UConcreteHeapRef
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsvmCollectionDomainProjectorTest {
    @Test
    fun `array length and every active element satisfy their recursive domains`() {
        val domain = ArrayDomain(
            element = IntegerDomain(min = -1, max = 1),
            minLength = 1,
            maxLength = 2,
        )

        assertTrue(acceptsArray(domain, length = 1, elements = listOf(-1.0)))
        assertTrue(acceptsArray(domain, length = 2, elements = listOf(-1.0, 1.0)))
        assertFalse(acceptsArray(domain, length = 0, elements = emptyList()))
        assertFalse(acceptsArray(domain, length = 3, elements = listOf(0.0, 0.0)))
        assertFalse(acceptsArray(domain, length = 2, elements = listOf(0.0, 2.0)))
    }

    @Test
    fun `tuple has exact length and positional recursive domains`() {
        val domain = TupleDomain(listOf(IntegerDomain(min = 2, max = 4), BooleanDomain))

        assertTrue(acceptsTuple(domain, number = 3.0, boolean = true, length = 2))
        assertTrue(acceptsTuple(domain, number = 4.0, boolean = false, length = 2))
        assertFalse(acceptsTuple(domain, number = 1.0, boolean = true, length = 2))
        assertFalse(acceptsTuple(domain, number = 3.0, boolean = true, length = 1))
    }

    @Test
    fun `oversized arrays are rejected before materialization`() {
        val domain = ArrayDomain(IntegerDomain(), maxLength = 3)
        val manifest = manifest(domain, exportName = "acceptsNumberArray")
        val mapping = mapper.map(manifest)
        val target = mapping.predicate.targets.single()
        val projector = UsvmDomainProjector(
            options = UsvmProjectionOptions(maxSymbolicCollectionLength = 2),
        )

        assertFailsWith<IllegalArgumentException> {
            analyze(target.method) { state ->
                projector.configure(state, manifest.inputs, target.bindings.inputs)
            }
        }
    }

    private fun acceptsArray(domain: ArrayDomain, length: Int, elements: List<Double>): Boolean {
        val manifest = manifest(domain, exportName = "acceptsNumberArray")
        val mapping = mapper.map(manifest)
        val target = mapping.predicate.targets.single()

        return runCatchingAnalyze(target.method) { state ->
            val projection = projector.configure(state, manifest.inputs, target.bindings.inputs)

            with(state.ctx) {
                val array = projection.inputs.single().value.asExpr(addressSort)
                val arrayType = EtsArrayType(EtsNumberType, dimensions = 1)
                val projectedLength = state.memory.read(mkArrayLengthLValue(array, arrayType))
                state.pathConstraints += mkEq(projectedLength, mkBv(length))
                elements.forEachIndexed { index, element ->
                    val lValue = mkArrayIndexLValue(fp64Sort, array, mkBv(index), arrayType)
                    val projectedElement = state.memory.read(lValue)

                    state.pathConstraints += mkEq(projectedElement, mkFp(element, fp64Sort))
                }
            }
        }
    }

    private fun acceptsTuple(
        domain: TupleDomain,
        number: Double,
        boolean: Boolean,
        length: Int,
    ): Boolean {
        val manifest = manifest(domain, exportName = "acceptsNumberBooleanTuple")
        val mapping = mapper.map(manifest)
        val target = mapping.predicate.targets.single()

        return runCatchingAnalyze(target.method) { state ->
            val projection = projector.configure(state, manifest.inputs, target.bindings.inputs)

            with(state.ctx) {
                val tuple = projection.inputs.single().value.asExpr(addressSort)
                val arrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
                val projectedLength = state.memory.read(mkArrayLengthLValue(tuple, arrayType))
                val numberBox = state.memory.read(
                    mkArrayIndexLValue(addressSort, tuple, mkBv(0), arrayType),
                ) as UConcreteHeapRef
                val booleanBox = state.memory.read(
                    mkArrayIndexLValue(addressSort, tuple, mkBv(1), arrayType),
                ) as UConcreteHeapRef

                state.pathConstraints += mkEq(projectedLength, mkBv(length))
                state.pathConstraints += mkEq(numberBox.extractFp(state.memory), mkFp(number, fp64Sort))
                state.pathConstraints += mkEq(booleanBox.extractBool(state.memory), mkBool(boolean))
            }
        }
    }

    private fun manifest(domain: PropertyDomain, exportName: String) = PropertyManifest(
        propertyId = "usvm.collection.$exportName",
        inputs = listOf(PropertyInput(name = "value", domain = domain)),
        predicate = TypeScriptEntryPoint(
            module = "UsvmCapabilityFixture.ts",
            exportName = exportName,
        ),
    )

    private fun runCatchingAnalyze(
        method: org.jacodb.ets.model.EtsMethod,
        configure: (TsState) -> Unit,
    ): Boolean = runCatching { analyze(method, configure) }.isSuccess

    private fun analyze(
        method: org.jacodb.ets.model.EtsMethod,
        configure: (TsState) -> Unit,
    ) {
        TsMachine(
            scene = scene,
            options = UMachineOptions(stateCollectionStrategy = StateCollectionStrategy.ALL),
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyze(
                methods = listOf(method),
                configureInitialState = { _, state -> configure(state) },
            )
        }
    }

    companion object {
        private lateinit var scene: EtsScene
        private lateinit var mapper: PropertyEtsMapper
        private val projector = UsvmDomainProjector()

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/usvm/UsvmCapabilityFixture.ts")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            scene = EtsScene(listOf(file))
            mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(source.parent))
        }
    }
}
