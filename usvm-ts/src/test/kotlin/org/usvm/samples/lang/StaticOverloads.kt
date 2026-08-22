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
