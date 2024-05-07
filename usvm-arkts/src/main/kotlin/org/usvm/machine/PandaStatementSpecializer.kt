package org.usvm.machine

import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.asExpr
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallInst
import org.jacodb.api.common.cfg.CommonGotoInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaCallInst
import org.jacodb.panda.dynamic.api.PandaCatchInst
import org.jacodb.panda.dynamic.api.PandaEmptyBBPlaceholderInst
import org.jacodb.panda.dynamic.api.PandaGotoInst
import org.jacodb.panda.dynamic.api.PandaIfInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaInstVisitor
import org.jacodb.panda.dynamic.api.PandaLocal
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaObjectType
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaThrowInst
import org.jacodb.panda.dynamic.api.TODOInst
import org.usvm.UBoolExpr
import org.usvm.machine.state.PandaState

class PandaStatementSpecializer(
    private val localIdxMapper: (PandaMethod, PandaLocal) -> Int,
    private val prevBBId: Int
) : PandaInstVisitor<PandaInst> {
    private var stepScope: PandaStepScope? = null
    private var somethingWasSpecialized: Boolean = false

    fun specialize(scope: PandaStepScope, inst: PandaInst): Boolean {
        stepScope = scope

        inst.accept(this)

        return somethingWasSpecialized.also {
            stepScope = null
            somethingWasSpecialized = false
        }
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
        if (inst.rhv !is PandaBinaryExpr || inst.rhv is PandaBinaryOperationAuxiliaryExpr) {
            return inst
        }

        val rhv = inst.rhv as PandaBinaryExpr

        val stepScope = requireNotNull(stepScope)

        val types = listOf(PandaNumberType, PandaBoolType, PandaStringType, PandaObjectType)

        val exprResolver = PandaExprResolver(stepScope.calcOnState { ctx }, stepScope, localIdxMapper, prevBBId)

        val conditions: List<Pair<UBoolExpr, PandaState.() -> Unit>> = stepScope.calcOnState {
            val (lhs, rhs) = rhv.operands.let {
                exprResolver.resolvePandaExpr(it.first()) to exprResolver.resolvePandaExpr(it.last())
            }

            if (lhs is KInterpretedValue && rhs is KInterpretedValue) {
                val fstType = ctx.nonRefSortToType(lhs.sort)
                val sndType = ctx.nonRefSortToType(lhs.sort)

                return@calcOnState listOf(ctx.trueExpr to { state: PandaState ->
                    val newExpr = PandaBinaryOperationAuxiliaryExpr.specializeBinaryOperation(rhv, fstType, sndType)
                    state.newStmt(PandaAssignInst(inst.location, inst.lhv, newExpr))
                })
            }

            // TODO partial cases

            val lhsRef = lhs?.asExpr(ctx.addressSort) ?: return@calcOnState listOf(ctx.falseExpr to {})
            val rhsRef = rhs?.asExpr(ctx.addressSort) ?: return@calcOnState listOf(ctx.falseExpr to {})

            types.flatMap { fstType ->
                types.map { sndType ->
                    ctx.mkAnd(
                        memory.types.evalIsSubtype(lhsRef, fstType),
                        memory.types.evalIsSubtype(rhsRef, sndType)
                    ) to { state: PandaState ->
                        val newExpr = PandaBinaryOperationAuxiliaryExpr.specializeBinaryOperation(rhv, fstType, sndType)
                        state.newStmt(PandaAssignInst(inst.location, inst.lhv, newExpr))
                    } // todo cast bool to int
                }
            }
        }

        somethingWasSpecialized = true
        stepScope.forkMulti(conditions)

        return inst
    }

    override fun visitPandaCallInst(inst: PandaCallInst): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitPandaCatchInst(inst: PandaCatchInst): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitPandaEmptyBBPlaceholderInst(inst: PandaEmptyBBPlaceholderInst): PandaInst {
        TODO("Not yet implemented")
    }

    override fun visitPandaGotoInst(inst: PandaGotoInst): PandaInst {
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
