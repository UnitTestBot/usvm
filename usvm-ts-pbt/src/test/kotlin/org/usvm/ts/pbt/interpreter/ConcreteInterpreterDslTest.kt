package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.dsl.add
import org.jacodb.ets.dsl.and
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.gt
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.lt
import org.jacodb.ets.dsl.mul
import org.jacodb.ets.dsl.neg
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.sub
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.dsl.ProgramBuilder
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.toEtsBlockCfg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConcreteInterpreterDslTest {

    private fun buildMethod(
        name: String,
        paramCount: Int,
        block: ProgramBuilder.() -> Unit,
    ): EtsMethod {
        val prog = program(block)
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature(
                    name = "Test",
                    file = EtsFileSignature(projectName = "TestP", fileName = "Test.ts"),
                ),
                name = name,
                parameters = (0 until paramCount).map { EtsMethodParameter(it, "p$it", EtsUnknownType) },
                returnType = EtsUnknownType,
            ),
        )
        method.body.cfg = prog.toBlockCfg().toEtsBlockCfg(method)
        return method
    }

    private val emptyScene = EtsScene(emptyList())
    private val interpreter = EtsConcreteInterpreter(emptyScene)

    private fun run(method: EtsMethod, vararg args: VValue): ExecutionResult =
        interpreter.execute(method, VUndefined, args.toList())

    @Test
    fun `abs function branches correctly`() {
        val abs = buildMethod("abs", 1) {
            val x = local("x")
            assign(x, param(0))
            ifStmt(lt(x, const(0.0))) {
                ret(neg(x))
            }
            ret(x)
        }
        assertEquals(ExecutionResult.Returned(VNumber(5.0)), run(abs, VNumber(-5.0)))
        assertEquals(ExecutionResult.Returned(VNumber(7.0)), run(abs, VNumber(7.0)))
        // abs(undefined): undefined < 0 is false -> returns undefined unchanged
        assertEquals(ExecutionResult.Returned(VUndefined), run(abs, VUndefined))
    }

    @Test
    fun `loop computes sum and terminates`() {
        // sum = 0; i = 0; while (i < n) { sum += i; i += 1 }; return sum
        val sum = buildMethod("sum", 1) {
            val n = local("n")
            val i = local("i")
            val s = local("s")
            assign(n, param(0))
            assign(i, const(0.0))
            assign(s, const(0.0))
            label("head")
            ifStmt(lt(i, n)) {
                assign(s, add(s, i))
                assign(i, add(i, const(1.0)))
                goto("head")
            }
            ret(s)
        }
        assertEquals(ExecutionResult.Returned(VNumber(45.0)), run(sum, VNumber(10.0)))
        assertEquals(ExecutionResult.Returned(VNumber(0.0)), run(sum, VNumber(0.0)))
    }

    @Test
    fun `infinite loop diverges by step budget`() {
        val loop = buildMethod("loop", 0) {
            label("head")
            ifStmt(eqq(const(1.0), const(1.0))) {
                goto("head")
            }
            ret(const(0.0))
        }
        val result = EtsConcreteInterpreter(emptyScene, ExecutionLimits(maxSteps = 1000)).execute(loop)
        assertTrue(result is ExecutionResult.Diverged)
    }

    @Test
    fun `js coercion in arithmetic`() {
        // return p0 * 2 - p1
        val f = buildMethod("f", 2) {
            val r = local("r")
            assign(r, sub(mul(param(0), const(2.0)), param(1)))
            ret(r)
        }
        // "3" * 2 - true = 6 - 1 = 5
        assertEquals(ExecutionResult.Returned(VNumber(5.0)), run(f, VString("3"), VBool(true)))
        // undefined -> NaN
        val r = run(f, VUndefined, VNumber(0.0))
        assertTrue(((r as ExecutionResult.Returned).value as VNumber).value.isNaN())
    }

    @Test
    fun `branch listener records edges`() {
        val f = buildMethod("g", 1) {
            val x = local("x")
            assign(x, param(0))
            ifStmt(and(gt(x, const(0.0)), lt(x, const(10.0)))) {
                ret(const(1.0))
            }
            ret(const(0.0))
        }
        val edges = mutableListOf<Pair<EtsIfStmt, EtsStmt>>()
        val conditions = mutableListOf<Boolean>()
        val listener = object : ExecutionListener {
            override fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) {
                edges += ifStmt to taken
                conditions += condition
            }
        }
        interpreter.execute(f, VUndefined, listOf(VNumber(5.0)), listener)
        assertTrue(conditions.isNotEmpty())
        assertTrue(conditions.last()) // 0 < 5 < 10 -> true branch taken

        conditions.clear()
        interpreter.execute(f, VUndefined, listOf(VNumber(-1.0)), listener)
        assertTrue(conditions.contains(false))
    }
}
