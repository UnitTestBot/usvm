package org.usvm.jacodb

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallInst
import org.jacodb.api.common.cfg.CommonGotoInst
import org.jacodb.api.common.cfg.CommonIfInst
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoCallInst
import org.jacodb.go.api.GoDebugRefInst
import org.jacodb.go.api.GoDeferInst
import org.jacodb.go.api.GoExprVisitor
import org.jacodb.go.api.GoGoInst
import org.jacodb.go.api.GoIfInst
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoInstVisitor
import org.jacodb.go.api.GoJumpInst
import org.jacodb.go.api.GoMapUpdateInst
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoPanicInst
import org.jacodb.go.api.GoReturnInst
import org.jacodb.go.api.GoRunDefersInst
import org.jacodb.go.api.GoSendInst
import org.jacodb.go.api.GoStoreInst
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.jacodb.interpreter.GoStepScope

class GoInstVisitor(
    private val ctx: GoContext,
    private val scope: GoStepScope,
    private val exprVisitor: GoExprVisitor<UExpr<out USort>>,
) : GoInstVisitor<GoInst> {
    override fun visitGoJumpInst(inst: GoJumpInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoIfInst(inst: GoIfInst): GoInst = with(ctx) {
        val pos = inst.location.method.blocks[inst.trueBranch.index].insts[0]
        val neg = inst.location.method.blocks[inst.falseBranch.index].insts[0]

        scope.forkWithBlackList(
            inst.condition.accept(exprVisitor).asExpr(boolSort),
            pos,
            neg,
            blockOnTrueState = { newInst(pos) },
            blockOnFalseState = { newInst(neg) }
        )
        GoNullInst(inst.location.method)
    }

    override fun visitGoReturnInst(inst: GoReturnInst): GoInst {
        scope.doWithState {
            returnValue(inst.retValue[0].accept(exprVisitor).cast())
        }
        return GoNullInst(inst.location.method)
    }

    override fun visitGoRunDefersInst(inst: GoRunDefersInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoPanicInst(inst: GoPanicInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoGoInst(inst: GoGoInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoDeferInst(inst: GoDeferInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoSendInst(inst: GoSendInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoStoreInst(inst: GoStoreInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoMapUpdateInst(inst: GoMapUpdateInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoDebugRefInst(inst: GoDebugRefInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonAssignInst(inst: CommonAssignInst<*, *>): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonCallInst(inst: CommonCallInst<*, *>): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonGotoInst(inst: CommonGotoInst<*, *>): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonIfInst(inst: CommonIfInst<*, *>): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitCommonReturnInst(inst: CommonReturnInst<*, *>): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonInst(inst: CommonInst<*, *>): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitExternalGoInst(inst: GoInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoAssignInst(inst: GoAssignInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoCallInst(inst: GoCallInst): GoInst {
        TODO("Not yet implemented")
    }
}
