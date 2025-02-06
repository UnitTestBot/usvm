package org.usvm.samples

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.eq
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.neq
import org.jacodb.ets.dsl.or
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.thisRef
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.graph.linearize
import org.jacodb.ets.graph.toEtsBlockCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.getLocals
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import org.usvm.util.isTruthy

class Or : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Or.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    private val classSignature: EtsClassSignature =
        scene.projectFiles[0].classes.single { it.name != DEFAULT_ARK_CLASS_NAME }.signature

    @Test
    fun `test orOfBooleanAndBoolean`() {
        val method = getMethod("Or", "orOfBooleanAndBoolean")
        discoverProperties<TSObject.TSBoolean, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && b.value && r.number == 1.0 },
            { a, b, r -> a.value && !b.value && r.number == 2.0 },
            { a, b, r -> !a.value && b.value && r.number == 3.0 },
            { a, b, r -> !a.value && !b.value && r.number == 4.0 },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 },
                { _, _, r -> r.number in 1.0..4.0 },
            )
        )
    }

    @Test
    fun `test orOfNumberAndNumber`() {
        val prog = program {
            val a = local("a")
            val b = local("b")

            assign(a, param(0))
            assign(b, param(1))
            assign(local("this"), thisRef())

            ifStmt(or(a, b)) {
                ifStmt(a) {
                    ifStmt(b) {
                        ret(const(1.0))
                    }
                    ifStmt(neq(b, b)) {
                        ret(const(2.0))
                    }
                    ifStmt(eq(b, const(0.0))) {
                        ret(const(3.0))
                    }
                    // ret(const(0.0))
                }
                ifStmt(neq(a, a)) {
                    ifStmt(b) {
                        ret(const(4.0))
                    }
                    // ret(const(0.0))
                }
                ifStmt(eq(a, const(0.0))) {
                    ifStmt(b) {
                        ret(const(7.0))
                    }
                    // ret(const(0.0))
                }
                // ret(const(0.0))
            }
            ifStmt(neq(a, a)) {
                ifStmt(neq(b, b)) {
                    ret(const(5.0))
                }
                ifStmt(eq(b, const(0.0))) {
                    ret(const(6.0))
                }
                // ret(const(0.0))
            }
            ifStmt(eq(a, const(0.0))) {
                ifStmt(neq(b, b)) {
                    ret(const(8.0))
                }
                ifStmt(eq(b, const(0.0))) {
                    ret(const(9.0))
                }
                // ret(const(0.0))
            }
            ret(const(0.0))
        }
        println("Program:\n${prog.toText()}")
        val blockCfg = prog.toBlockCfg()

        val locals = mutableListOf<EtsLocal>()
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "orOfNumberAndNumber",
                parameters = listOf(
                    EtsMethodParameter(0, "a", EtsNumberType),
                    EtsMethodParameter(1, "b", EtsNumberType),
                ),
                returnType = EtsNumberType,
            ),
            locals = locals,
        )

        val etsBlockCfg = blockCfg.toEtsBlockCfg(method)
        val etsCfg = etsBlockCfg.linearize()

        method._cfg = etsCfg
        locals.clear()
        locals += method.getLocals()

        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && b.number.isNaN() && r.number == 2.0 },
            { a, b, r -> isTruthy(a) && b.number == 0.0 && r.number == 3.0 },
            { a, b, r -> a.number.isNaN() && isTruthy(b) && r.number == 4.0 },
            { a, b, r -> a.number.isNaN() && b.number.isNaN() && r.number == 5.0 },
            { a, b, r -> a.number.isNaN() && b.number == 0.0 && r.number == 6.0 },
            { a, b, r -> a.number == 0.0 && isTruthy(b) && r.number == 7.0 },
            { a, b, r -> a.number == 0.0 && b.number.isNaN() && r.number == 8.0 },
            { a, b, r -> a.number == 0.0 && b.number == 0.0 && r.number == 9.0 },
            invariants = arrayOf(
                { _, _, r -> r.number != 0.0 },
                { _, _, r -> r.number in 1.0..9.0 },
            )
        )
    }
}
