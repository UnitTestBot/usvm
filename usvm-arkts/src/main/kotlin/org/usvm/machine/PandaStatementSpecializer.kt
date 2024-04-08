package org.usvm.machine

import io.ksmt.utils.asExpr
import org.graalvm.compiler.bytecode.Bytecodes.operator
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallInst
import org.jacodb.api.common.cfg.CommonGotoInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaCallInst
import org.jacodb.panda.dynamic.api.PandaIfInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaInstVisitor
import org.jacodb.panda.dynamic.api.PandaLocal
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaThrowInst
import org.jacodb.panda.dynamic.api.TODOInst

class PandaStatementSpecializer(
    private val localIdxMapper: (PandaMethod, PandaLocal) -> Int
) : PandaInstVisitor<PandaInst> {
    private var stepScope: PandaStepScope? = null
    private var wasForked: Boolean = false

    fun specialize(scope: PandaStepScope, inst: PandaInst): Boolean {
        if (inst is PandaBinaryOperationAuxiliaryExpr) {
            return false
        }

        stepScope = scope
        return inst.accept(this).also { stepScope = null }
    }

    override fun visitCommonAssignInst(inst: CommonAssignInst<*, *>): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonCallInst(inst: CommonCallInst<*, *>): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonGotoInst(inst: CommonGotoInst<*, *>): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonIfInst(inst: CommonIfInst<*, *>): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonReturnInst(inst: CommonReturnInst<*, *>): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonInst(inst: CommonInst<*, *>): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitPandaAssignInst(inst: PandaAssignInst): PandaInst {
        val stepScope = requireNotNull(stepScope)

        val types = listOf(PandaNumberType, PandaBoolType, /*PandaStringType*/)

        val exprs = types.flatMap { fst ->
            types.map { snd ->
                PandaBinaryOperationAuxiliaryExpr.specializeBinaryOperation(inst.rhv, fst, snd)
            }
        }

        if (exprs.singleOrNull() == inst.rhv) {
            return inst
        }

        val exprResolver = PandaExprResolver(stepScope.calcOnState { ctx }, stepScope, localIdxMapper)

        val conditions = stepScope.calcOnState {
            val (lhs, rhs) = inst.rhv.operands.let {
                exprResolver.resolvePandaExpr(it.first()) to exprResolver.resolvePandaExpr(it.last())
            }

            val lhsRef = lhs?.asExpr(ctx.addressSort) ?: return@calcOnState
            val rhsRef = rhs?.asExpr(ctx.addressSort) ?: return@calcOnState

            types.flatMap { fstType ->
                types.map { sndType ->
                    ctx.mkAnd(
                        memory.types.evalIsSubtype(lhsRef, fstType),
                        memory.types.evalIsSubtype(rhsRef, sndType)
                    ) to {
                        newStmt(TODO)
                    } // todo cast bool to int
                }
            }
        }
    }

    override fun visitPandaCallInst(inst: PandaCallInst): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitPandaIfInst(inst: PandaIfInst): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitPandaReturnInst(inst: PandaReturnInst): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitPandaThrowInst(inst: PandaThrowInst): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitTODOInst(inst: TODOInst): PandaInst {
        TODO("Not yet implemented")
    }
}