package org.usvm.reachability

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StoredFunctionCallTest {
    private val file = loadEtsFileAutoConvert(getResourcePath("/reachability/StoredFunctionCall.ts"))
    private val scene = EtsScene(listOf(file))
    private val options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        timeout = 5.seconds,
        solverTimeout = 1.seconds,
        solverType = SolverType.YICES,
        exceptionsPropagation = true,
        stepsFromLastCovered = 10_000L,
    )

    @Test
    fun `constructor callback stored in a field can return`() {
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == "exercise" }

        val machine = TsMachine(scene, options, TsOptions())
        val results = machine.analyze(listOf(method))

        assertTrue(results.isNotEmpty(), "Expected a completed path through the stored callback")
    }

    @Test
    fun `symbolic object field can select both branches`() {
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == "inspect" }
        val returns = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()

        val machine = TsMachine(scene, options, TsOptions())
        val results = machine.analyze(listOf(method))
        val reached = results.flatMap { it.pathNode.allStatements }.toSet()

        assertTrue(returns.size >= 2 && returns.all { it in reached }, "Expected both object-field branches")
    }
}
