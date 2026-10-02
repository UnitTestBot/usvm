package org.usvm.samples.lang

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.eq
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StoredFunctionCall : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/StoredFunctionCall.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test callback stored in constructor`() {
        val method = getMethod("constructorCallback")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 2 },
            invariants = arrayOf({ result -> result eq 2 }),
        )
    }

    @Test
    fun `test regular function uses call receiver`() {
        val method = getMethod("regularFunction")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 5 },
            invariants = arrayOf({ result -> result eq 5 }),
        )
    }

    @Test
    fun `test callback field inherited from base class`() {
        val method = getMethod("inheritedField")
        val derived = scene.projectClasses.single { it.name == "DerivedStoredFunctionCall" }
        val parameterSlot = 1
        val machine = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            initialStateConfigurator = { state ->
                with(state.ctx) {
                    val receiver = state.memory.allocConcrete(derived.type)
                    state.memory.write(
                        mkRegisterStackLValue(sort = addressSort, idx = parameterSlot),
                        receiver.asExpr(addressSort),
                        guard = trueExpr,
                    )
                    state.saveSortForLocal(idx = parameterSlot, sort = addressSort)
                }
            },
        )

        val states = machine.use { it.analyze(listOf(method)) }
        val results = states.map { state ->
            val result = TsTestResolver().resolve(method, state).returnValue
            assertIs<TsTestValue.TsNumber>(result).number
        }.toSet()

        assertEquals(setOf(7.0), results)
    }
}
