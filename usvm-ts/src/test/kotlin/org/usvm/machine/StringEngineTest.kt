package org.usvm.machine

import io.ksmt.sort.KBv16Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.sizeSort
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.mkStringFromCodeUnits
import org.usvm.util.resolveStringFromModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration

class StringEngineTest {
    private val file = loadEtsFileAutoConvert(
        getResourcePath("/models/SymbolicStringEngine.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(file))

    @Test
    fun `length uses UTF-16 units through a widened alias`() {
        val direct = execute("length")
        val alias = execute("anyAliasLength")

        assertEquals(2.0, assertIs<TsTestValue.TsNumber>(direct).number)
        assertEquals(1.0, assertIs<TsTestValue.TsNumber>(alias).number)
    }

    @Test
    fun `character operations preserve isolated surrogate units`() {
        val code = execute("charCodeAt")
        val charEquals = execute("charAtEquals")
        val indexEquals = execute("indexedReadEquals")
        val outOfRange = execute("outOfRangeCharAt")

        assertEquals(0xd800.toDouble(), assertIs<TsTestValue.TsNumber>(code).number)
        assertEquals(true, assertIs<TsTestValue.TsBoolean>(charEquals).value)
        assertEquals(true, assertIs<TsTestValue.TsBoolean>(indexEquals).value)
        assertEquals("", assertIs<TsTestValue.TsString>(outOfRange).value)
    }

    @Test
    fun `shared allocation and decoding preserve a solver selected surrogate`() {
        val method = scene.projectClasses.single { it.name == "SymbolicStringEngine" }
            .methods
            .single { it.name == "inputRoundtrip" }
        lateinit var input: UConcreteHeapRef
        val options = UMachineOptions(stateCollectionStrategy = StateCollectionStrategy.ALL)

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
                            mkRegisterStackLValue(addressSort, 1),
                            input.asExpr(addressSort),
                            guard = trueExpr,
                        )
                    }
                },
            )

            val state = states.single()
            assertEquals("\ud800", state.resolveStringFromModel(model = state.models.single(), ref = input))
            val decoded = TsTestResolver().resolve(method, state).returnValue
            assertEquals("\ud800", assertIs<TsTestValue.TsString>(decoded).value)
        }
    }

    private fun execute(name: String): TsTestValue {
        val method = scene.projectClasses.single { it.name == "SymbolicStringEngine" }
            .methods
            .single { it.name == name }
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )

        return TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            val state = machine.analyze(listOf(method)).single()
            TsTestResolver().resolve(method, state).returnValue
        }
    }
}
