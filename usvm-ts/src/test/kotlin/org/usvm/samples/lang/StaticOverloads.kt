package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.test.util.checkers.eq as exactly
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import kotlin.test.assertEquals

class StaticOverloads : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/StaticOverloads.ts")

    @Test
    fun `test static overload group executes once`() {
        val method = getMethod("callOverloaded")
        checkMatches<TsTestValue.TsNumber>(
            method = method,
            analysisResultNumberMatches = exactly(1),
            { result -> result eq 42 },
        )
    }

    @Test
    fun `static call selects static body when instance body shares its name`() {
        val method = getMethod("callStaticWithSameNameInstance")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 42 },
            invariants = arrayOf({ result -> result eq 42 }),
        )
    }

    @Test
    fun `super call selects instance body when static body shares its name`() {
        val method = getMethod("callSuperWithSameNameStatic")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 11 },
            invariants = arrayOf({ result -> result eq 11 }),
        )
    }

    @Test
    fun `test static overload group does not fork equivalent states`() {
        var forkedStates = 0
        val observer = object : UMachineObserver<TsState> {
            override fun onState(parent: TsState, forks: Sequence<TsState>) {
                forkedStates += forks.count()
            }
        }
        val method = getMethod("callOverloaded")

        TsMachine(scene, options, TsOptions(), machineObserver = observer).use { machine ->
            machine.analyze(listOf(method))
        }

        assertEquals(0, forkedStates)
    }
}
