package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcePath
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkRegisterStackLValue
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsvmScalarDomainProjectorTest {
    @Test
    fun `bounded integer accepts exactly integral non-negative-zero values inside inclusive bounds`() {
        val domain = IntegerDomain(min = -2, max = 3)

        listOf(-2.0, 0.0, 3.0).forEach { value ->
            assertTrue(acceptsNumber(domain, value), "Expected $value to be accepted")
        }
        listOf(-3.0, 4.0, 0.5, Double.NaN, Double.POSITIVE_INFINITY, -0.0).forEach { value ->
            assertFalse(acceptsNumber(domain, value), "Expected $value to be rejected")
        }
    }

    @Test
    fun `number bounds and NaN policy use shared JavaScript number semantics`() {
        val bounded = NumberDomain(
            min = JsNumber.finite(-1.5),
            max = JsNumber.finite(2.5),
            allowNaN = false,
        )
        val unboundedWithNaN = NumberDomain(allowNaN = true)

        assertTrue(acceptsNumber(bounded, -1.5))
        assertTrue(acceptsNumber(bounded, 2.5))
        assertFalse(acceptsNumber(bounded, -1.6))
        assertFalse(acceptsNumber(bounded, 2.6))
        assertFalse(acceptsNumber(bounded, Double.NaN))
        assertTrue(acceptsNumber(unboundedWithNaN, Double.NaN))
    }

    @Test
    fun `primitive constants admit only their declared JavaScript value`() {
        val booleanDomain = ConstantDomain(JsConcreteValue.Boolean(value = true))
        val numberDomain = ConstantDomain(JsConcreteValue.number(7.25))

        assertTrue(acceptsBoolean(booleanDomain, value = true))
        assertFalse(acceptsBoolean(booleanDomain, value = false))
        assertTrue(acceptsNumber(numberDomain, value = 7.25))
        assertFalse(acceptsNumber(numberDomain, value = 7.0))
    }

    @Test
    fun `optional number admits its nested domain or exactly undefined`() {
        val domain = OptionalDomain(
            value = IntegerDomain(min = 1, max = 3),
            nil = JsConcreteValue.Undefined,
        )

        assertTrue(
            acceptsOptional(domain) { state, projection ->
                with(state.ctx) {
                    val value = projection.inputs.single().value as UConcreteHeapRef
                    val type = value.getFakeType(state.memory)

                    mkAnd(type.fpTypeExpr, mkFpEqualExpr(value.extractFp(state.memory), mkFp(2.0, fp64Sort)))
                }
            },
        )
        assertTrue(
            acceptsOptional(domain) { state, projection ->
                with(state.ctx) {
                    val value = projection.inputs.single().value as UConcreteHeapRef
                    val type = value.getFakeType(state.memory)

                    mkAnd(type.refTypeExpr, mkHeapRefEq(value.extractRef(state.memory), mkUndefinedValue()))
                }
            },
        )
        assertFalse(
            acceptsOptional(domain) { state, projection ->
                with(state.ctx) {
                    val value = projection.inputs.single().value as UConcreteHeapRef
                    val type = value.getFakeType(state.memory)

                    mkAnd(type.fpTypeExpr, mkFpEqualExpr(value.extractFp(state.memory), mkFp(4.0, fp64Sort)))
                }
            },
        )
        assertFalse(
            acceptsOptional(domain) { state, projection ->
                with(state.ctx) {
                    val value = projection.inputs.single().value as UConcreteHeapRef
                    val type = value.getFakeType(state.memory)

                    mkAnd(type.refTypeExpr, mkHeapRefEq(value.extractRef(state.memory), mkTsNullValue()))
                }
            },
        )
    }

    @Test
    fun `string projection constrains inclusive UTF-16 length bounds`() {
        val domain = StringDomain(minLength = 1, maxLength = 3)

        assertTrue(acceptsStringLength(domain, length = 1))
        assertTrue(acceptsStringLength(domain, length = 3))
        assertFalse(acceptsStringLength(domain, length = 0))
        assertFalse(acceptsStringLength(domain, length = 4))
    }

    private fun acceptsNumber(domain: PropertyDomain, value: Double): Boolean = acceptsScalar(
        domain = domain,
        exportName = "acceptsNumber",
    ) { state ->
        with(state.ctx) {
            val input = state.memory.read(mkRegisterStackLValue(fp64Sort, 1)).asExpr(fp64Sort)

            mkEq(input, mkFp(value, fp64Sort))
        }
    }

    private fun acceptsBoolean(domain: PropertyDomain, value: Boolean): Boolean = acceptsScalar(
        domain = domain,
        exportName = "acceptsBoolean",
    ) { state ->
        with(state.ctx) {
            val input = state.memory.read(mkRegisterStackLValue(boolSort, 1)).asExpr(boolSort)

            mkEq(input, mkBool(value))
        }
    }

    private fun acceptsOptional(
        domain: OptionalDomain,
        constraint: (TsState, UsvmDeclaredDomainProjection) -> UBoolExpr,
    ): Boolean {
        val manifest = manifest(domain, exportName = "acceptsOptionalNumber")
        val mapping = mapper.map(manifest)
        val target = mapping.predicate.targets.single()

        return analyze(target.method) { state ->
            val projection = projector.configure(state, manifest.inputs, target.bindings.inputs)

            state.pathConstraints += constraint(state, projection)
        }
    }

    private fun acceptsStringLength(domain: StringDomain, length: Int): Boolean {
        val manifest = manifest(domain, exportName = "acceptsString")
        val mapping = mapper.map(manifest)
        val target = mapping.predicate.targets.single()

        return analyze(target.method) { state ->
            val projection = projector.configure(state, manifest.inputs, target.bindings.inputs)

            with(state.ctx) {
                val value = projection.inputs.single().value.asExpr(addressSort)
                val arrayType = EtsArrayType(EtsStringType, dimensions = 1)
                val projectedLength = state.memory.read(mkArrayLengthLValue(value, arrayType))

                state.pathConstraints += mkEq(projectedLength, mkBv(length))
            }
        }
    }

    private fun acceptsScalar(
        domain: PropertyDomain,
        exportName: String,
        constraint: (TsState) -> UBoolExpr,
    ): Boolean {
        val manifest = manifest(domain, exportName)
        val mapping = mapper.map(manifest)
        val target = mapping.predicate.targets.single()

        return analyze(target.method) { state ->
            projector.configure(state, manifest.inputs, target.bindings.inputs)
            state.pathConstraints += constraint(state)
        }
    }

    private fun analyze(
        method: org.jacodb.ets.model.EtsMethod,
        configure: (TsState) -> Unit,
    ): Boolean = runCatching {
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
    }.isSuccess

    private fun manifest(domain: PropertyDomain, exportName: String) = PropertyManifest(
        propertyId = "usvm.scalar.$exportName",
        inputs = listOf(PropertyInput(name = "value", domain = domain)),
        predicate = TypeScriptEntryPoint(
            module = "UsvmCapabilityFixture.ts",
            exportName = exportName,
        ),
    )

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
