package org.usvm.machine

import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmtLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.util.TsTestResolver
import kotlin.time.Duration.Companion.seconds

/**
 * Direct tests for the inc/dec expression resolution.
 *
 * The IR is built programmatically: ArkAnalyzer never emits `++`/`--` unary
 * expressions (it expands them into `x := x + 1`), while the native ts-frontend
 * does emit them (as the value part, with an explicit write-back assignment),
 * so sample-based tests cannot exercise these visits.
 */
class IncDecExprTest {

    private fun buildMethod(name: String, makeExpr: (EtsEntity) -> EtsEntity): Pair<EtsScene, EtsMethodImpl> {
        val fileSig = EtsFileSignature(projectName = "test", fileName = "IncDec.ts")
        val classSig = EtsClassSignature(name = "T", file = fileSig)
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSig,
                name = name,
                parameters = listOf(EtsMethodParameter(0, "a", EtsNumberType)),
                returnType = EtsNumberType,
            ),
        )

        fun loc(i: Int) = EtsStmtLocation(method, i)
        val a = EtsLocal("a", EtsNumberType)
        val res = EtsLocal("res", EtsNumberType)
        val stmts = listOf(
            EtsAssignStmt(loc(0), a, EtsParameterRef(0, EtsNumberType)),
            EtsAssignStmt(loc(1), res, makeExpr(a)),
            EtsReturnStmt(loc(2), res),
        )
        method.body.cfg = EtsBlockCfg(
            blocks = listOf(BasicBlock(0, stmts)),
            successors = mapOf(0 to emptyList()),
        )
        method.body.locals = listOf(a, res)

        val cls = EtsClassImpl(signature = classSig, fields = emptyList(), methods = listOf(method))
        val file = EtsFile(signature = fileSig, classes = listOf(cls), namespaces = emptyList())
        return EtsScene(listOf(file)) to method
    }

    private fun run(name: String, makeExpr: (EtsEntity) -> EtsEntity): List<Pair<Double, Double>> {
        val (scene, method) = buildMethod(name, makeExpr)
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            exceptionsPropagation = true,
            timeout = 20.seconds,
            solverType = SolverType.YICES,
        )
        val states = TsMachine(scene, options, TsOptions()).use { machine ->
            machine.analyze(listOf(method))
        }
        assertTrue(states.isNotEmpty()) { "no states collected for $name" }
        return states.mapNotNull { state ->
            val test = TsTestResolver().resolve(method, state)
            val input = (test.before.parameters.firstOrNull() as? TsTestValue.TsNumber)?.number
            val result = (test.returnValue as? TsTestValue.TsNumber)?.number
            if (input != null && result != null) input to result else null
        }.also { assertTrue(it.isNotEmpty()) { "no numeric executions for $name" } }
    }

    @Test
    fun `pre-increment yields arg plus one`() {
        for ((input, result) in run("preInc") { EtsPreIncExpr(it, EtsNumberType) }) {
            if (input.isNaN()) assertTrue(result.isNaN())
            else assertEquals(input + 1, result, 0.0) { "++($input)" }
        }
    }

    @Test
    fun `pre-decrement yields arg minus one`() {
        for ((input, result) in run("preDec") { EtsPreDecExpr(it, EtsNumberType) }) {
            if (input.isNaN()) assertTrue(result.isNaN())
            else assertEquals(input - 1, result, 0.0) { "--($input)" }
        }
    }

    @Test
    fun `post-increment and post-decrement yield the old value`() {
        for ((input, result) in run("postInc") { EtsPostIncExpr(it, EtsNumberType) }) {
            if (input.isNaN()) assertTrue(result.isNaN())
            else assertEquals(input, result, 0.0) { "($input)++" }
        }
        for ((input, result) in run("postDec") { EtsPostDecExpr(it, EtsNumberType) }) {
            if (input.isNaN()) assertTrue(result.isNaN())
            else assertEquals(input, result, 0.0) { "($input)--" }
        }
    }
}
