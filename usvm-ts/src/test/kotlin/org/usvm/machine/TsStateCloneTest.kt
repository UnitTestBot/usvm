package org.usvm.machine

import io.mockk.mockk
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsTypeSystem
import org.usvm.util.EtsHierarchy
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Prepared clone ownership contract; source reachability and replay are separate concerns. */
class TsStateCloneTest {
    private val scene = EtsScene(projectFiles = emptyList())
    private val typeSystem = TsTypeSystem(
        scene = scene,
        typeOperationsTimeout = 1.seconds,
        hierarchy = EtsHierarchy(scene),
    )
    private val components = TsComponents(typeSystem = typeSystem, options = UMachineOptions(useSolverForForks = false))
    private val ctx = TsContext(scene = scene, components = components)
    private val state = TsState(
        ctx = ctx,
        ownership = MutabilityOwnership(),
        entrypoint = mockk(),
        maxStringLength = 0,
    )

    @AfterEach
    fun closeContext() {
        components.close()
        ctx.close()
    }

    @Test
    fun `cloned states isolate additions and removals of artificial locals`() {
        val retainedInClone = "retained-in-clone"
        val retainedInSource = "retained-in-source"
        val cloneOnlyLocal = "clone-only"
        val sourceOnlyLocal = "source-only"
        val initialLocals = setOf(retainedInClone, retainedInSource)
        state.addedArtificialLocals.addAll(initialLocals)

        val cloned = state.clone()

        assertEquals(initialLocals, cloned.addedArtificialLocals)

        cloned.addedArtificialLocals += cloneOnlyLocal
        cloned.addedArtificialLocals.remove(retainedInSource)

        assertEquals(initialLocals, state.addedArtificialLocals)
        assertEquals(setOf(retainedInClone, cloneOnlyLocal), cloned.addedArtificialLocals)

        state.addedArtificialLocals += sourceOnlyLocal
        state.addedArtificialLocals.remove(retainedInClone)

        assertEquals(setOf(retainedInSource, sourceOnlyLocal), state.addedArtificialLocals)
        assertEquals(setOf(retainedInClone, cloneOnlyLocal), cloned.addedArtificialLocals)
    }
}
