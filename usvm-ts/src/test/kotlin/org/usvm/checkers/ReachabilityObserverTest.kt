package org.usvm.checkers

import io.mockk.mockk
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TsComponents
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsState
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.TsTypeSystem
import org.usvm.targets.UTargetsSet
import org.usvm.util.EtsHierarchy
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ReachabilityObserverTest {
    private val scene = EtsScene(projectFiles = emptyList())
    private val typeSystem = TsTypeSystem(
        scene = scene,
        typeOperationsTimeout = 1.seconds,
        hierarchy = EtsHierarchy(scene),
    )
    private val components = TsComponents(
        typeSystem = typeSystem,
        options = UMachineOptions(useSolverForForks = false),
    )
    private val ctx = TsContext(scene = scene, components = components)

    @AfterEach
    fun closeContext() {
        components.close()
        ctx.close()
    }

    @Test
    fun `failed termination does not complete a shared target before a reachable termination`() {
        val statement = mockk<EtsStmt>()
        val target = TsReachabilityTarget.FinalPoint(statement)
        val failedState = stateAt(statement, target)
        val reachableState = stateAt(statement, target)
        val observer = ReachabilityObserver()

        observer.onStateTerminated(failedState, stateReachable = false)

        assertFalse(target.isRemoved)
        assertTrue(failedState.targets.reachedTerminal.isEmpty())

        observer.onStateTerminated(reachableState, stateReachable = true)

        assertTrue(target.isRemoved)
        assertTrue(target in reachableState.targets.reachedTerminal)
    }

    private fun stateAt(statement: EtsStmt, target: TsReachabilityTarget): TsState {
        val state = TsState(
            ctx = ctx,
            ownership = MutabilityOwnership(),
            entrypoint = mockk<EtsMethod>(),
            maxStringLength = 0,
            targets = UTargetsSet.from(listOf(target)),
        )
        state.newStmt(statement)

        return state
    }
}
