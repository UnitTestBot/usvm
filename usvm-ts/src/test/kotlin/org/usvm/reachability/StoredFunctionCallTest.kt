package org.usvm.reachability

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    fun `regular function stored in a field receives its call receiver`() {
        assertEquals(setOf(5.0), numberResults("regularFunction"))
    }

    @Test
    fun `subclass can call a function stored in an inherited field`() {
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == "inheritedField" }
        val derived = scene.projectClasses.single { it.name == "DerivedStoredFunctionCall" }
        val states = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            initialStateConfigurator = { state ->
                with(state.ctx) {
                    val receiver = state.memory.allocConcrete(derived.type)
                    state.memory.write(
                        mkRegisterStackLValue(addressSort, 1),
                        receiver.asExpr(addressSort),
                        guard = trueExpr,
                    )
                    state.saveSortForLocal(1, addressSort)
                }
            },
        ).use { machine -> machine.analyze(listOf(method)) }

        assertTrue(states.isNotEmpty(), "Expected a completed path through the inherited callback")
        val values = states.map { state ->
            val result = TsTestResolver().resolve(method, state).returnValue
            assertIs<TsTestValue.TsNumber>(result).number
        }.toSet()
        assertEquals(setOf(7.0), values)
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

    private fun numberResults(methodName: String): Set<Double> {
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == methodName }
        val states = TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyze(listOf(method))
        }

        assertTrue(states.isNotEmpty(), "Expected a completed path through $methodName")
        return states.map { state ->
            val result = TsTestResolver().resolve(method, state).returnValue
            assertIs<TsTestValue.TsNumber>(result).number
        }.toSet()
    }
}
