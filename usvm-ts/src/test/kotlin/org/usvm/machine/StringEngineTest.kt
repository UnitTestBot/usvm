package org.usvm.machine

import io.ksmt.sort.KBv16Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.sizeSort
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.mkStringFromCodeUnits
import org.usvm.util.resolveStringValue
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StringEngineTest : TsMethodTestRunner() {
    private val file = loadEtsFileAutoConvert(
        getResourcePath("/models/SymbolicStringEngine.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    override val scene = EtsScene(listOf(file))

    @Test
    fun `length uses UTF-16 units through a widened alias`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "length", className = "SymbolicStringEngine"),
            { value -> value.number == 2.0 },
            invariants = arrayOf({ value -> value.number == 2.0 }),
        )
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "anyAliasLength", className = "SymbolicStringEngine"),
            { value -> value.number == 1.0 },
            invariants = arrayOf({ value -> value.number == 1.0 }),
        )
    }

    /** Exercises the explicit code-unit allocation API, not source input discovery. */
    @Test
    fun `shared allocation and bounded decoding preserve a solver selected surrogate`() {
        val method = getMethod(methodName = "inputRoundtrip", className = "SymbolicStringEngine")
        lateinit var input: UConcreteHeapRef
        val options =
            UMachineOptions(solverType = SolverType.YICES, stateCollectionStrategy = StateCollectionStrategy.ALL)

        TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            val states = machine.analyze(
                methods = listOf(method),
                configureInitialState = { _, state ->
                    with(state.ctx) {
                        val length: UExpr<TsSizeSort> = state.makeSymbolicPrimitive(sizeSort)
                        val unit: UExpr<KBv16Sort> = state.makeSymbolicPrimitive(bv16Sort)
                        state.pathConstraints += mkEq(length, mkBv(1))
                        state.pathConstraints += mkEq(unit, mkBv(0xd800, bv16Sort))
                        input = state.mkStringFromCodeUnits(length = length, codeUnits = listOf(unit))
                        state.memory.write(
                            mkRegisterStackLValue(addressSort, idx = 1),
                            input.asExpr(addressSort),
                            guard = trueExpr,
                        )
                    }
                },
            )

            val state = states.single()

            assertEquals("\ud800", state.resolveStringValue(input, model = state.models.single(), maxLength = 1))

            val decoded = TsTestResolver().resolve(method, state).returnValue

            assertEquals("\ud800", assertIs<TsTestValue.TsString>(decoded).value)
        }
    }
}
