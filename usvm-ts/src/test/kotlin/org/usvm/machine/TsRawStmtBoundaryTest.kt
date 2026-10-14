package org.usvm.machine

import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.util.getResourcePath
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TsRawStmtBoundaryTest {
    @Test
    fun `reachable raw statement terminates as unsupported before the following return`() {
        val file = loadEtsFileAutoConvert(
            getResourcePath("/models/RawStatementBoundary.js"),
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val scene = EtsScene(projectFiles = listOf(file))
        val method = scene.projectClasses.flatMap { it.methods }
            .single { it.name == "rawWithSideEffect" }
        val rawStatement = method.cfg.stmts.filterIsInstance<EtsRawStmt>().single()
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = false,
        )

        val outcome = TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertTrue(rawStatement.kind == "UnsupportedStmt")
        assertTrue("WithStatement" in rawStatement.extra["kindName"].toString())
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { "WithStatement" in it }, outcome.unsupportedPaths.toString())
        assertFalse(outcome.engineFailed)
    }
}
