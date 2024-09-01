package org.usvm

import io.ksmt.expr.KConst
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoCallInst
import org.jacodb.go.api.GoDebugRefInst
import org.jacodb.go.api.GoDeferInst
import org.jacodb.go.api.GoGoInst
import org.jacodb.go.api.GoIfInst
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoInstVisitor
import org.jacodb.go.api.GoJumpInst
import org.jacodb.go.api.GoMapUpdateInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoPanicInst
import org.jacodb.go.api.GoReturnInst
import org.jacodb.go.api.GoRunDefersInst
import org.jacodb.go.api.GoSendInst
import org.jacodb.go.api.GoStoreInst
import org.jacodb.go.api.GoVar
import org.usvm.interpreter.GoStepScope
import org.usvm.memory.URegisterStackLValue
import org.usvm.statistics.ApplicationGraph

class GoInstVisitor(
    private val ctx: GoContext,
    private val pkg: GoPackage,
    private val scope: GoStepScope,
    private val exprVisitor: GoExprVisitor,
    private val applicationGraph: ApplicationGraph<GoMethod, GoInst>,
) : GoInstVisitor<GoInst> {
    override fun visitGoJumpInst(inst: GoJumpInst): GoInst {
        return inst.location.method.blocks[inst.target.index].instructions[0]
    }

    override fun visitGoIfInst(inst: GoIfInst): GoInst = with(ctx) {
        val pos = inst.location.method.blocks[inst.trueBranch.index].instructions[0]
        val neg = inst.location.method.blocks[inst.falseBranch.index].instructions[0]

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
            returnValue(inst.returnValues[0].accept(exprVisitor).cast())
        }
        return GoNullInst(inst.location.method)
    }

    override fun visitGoRunDefersInst(inst: GoRunDefersInst): GoInst {
        return scope.calcOnState {
            runDefers(lastEnteredMethod, inst)
            currentStatement
        }
    }

    override fun visitGoPanicInst(inst: GoPanicInst): GoInst {
        val value = inst.throwable.accept(exprVisitor)

        return scope.calcOnState {
            panic(value, inst.throwable.type)
            currentStatement
        }
//        return GoNullInst(inst.location.method)
    }

    override fun visitGoGoInst(inst: GoGoInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoDeferInst(inst: GoDeferInst): GoInst {
        val name = (inst.func.accept(exprVisitor) as KConst).toString()
        val method = ctx.getClosure(name)

        val parameters = inst.args.map { it.accept(exprVisitor) }.toTypedArray()
        val call = GoCall(method, applicationGraph.entryPoints(method).first(), parameters)
        ctx.setMethodInfo(method, parameters)

        scope.doWithState {
            data.addDeferredCall(lastEnteredMethod, call)
        }
        return next(inst)
    }

    override fun visitGoSendInst(inst: GoSendInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoStoreInst(inst: GoStoreInst): GoInst {
        val pointer = inst.lhv.accept(exprVisitor) as UAddressPointer
        val rvalue = inst.rhv.accept(exprVisitor)
        val lvalue = exprVisitor.pointerLValue(pointer, rvalue.sort)
        scope.doWithState {
            memory.write(lvalue, rvalue.asExpr(rvalue.sort), ctx.trueExpr)
        }

        return next(inst)
    }

    override fun visitGoMapUpdateInst(inst: GoMapUpdateInst): GoInst {
        TODO("Not yet implemented")
    }

    override fun visitGoDebugRefInst(inst: GoDebugRefInst): GoInst {
        return unsupportedInst("DebugRef")
    }

    override fun visitExternalGoInst(inst: GoInst): GoInst {
        return unsupportedInst("External")
    }

    override fun visitGoAssignInst(inst: GoAssignInst): GoInst {
        val index = index((inst.lhv as GoVar).name)
        val rvalue = inst.rhv.accept(exprVisitor)
        val sort = rvalue.sort

        if (rvalue == ctx.nullRef) {
            return GoNullInst(inst.location.method)
        }

        scope.doWithState {
            memory.write(URegisterStackLValue(sort, index), rvalue.asExpr(sort), ctx.trueExpr)
        }

        return next(inst)
    }

    override fun visitGoCallInst(inst: GoCallInst): GoInst {
        return unsupportedInst("Call")
    }

    private fun next(inst: GoInst): GoInst {
        return applicationGraph.successors(inst).ifEmpty { sequenceOf(GoNullInst(inst.location.method)) }.first()
    }

    private fun unsupportedInst(name: String): GoInst {
        throw UnsupportedOperationException("Instruction '$name' not supported")
    }

    private fun index(name: String): Int {
        return name.substring(1).toInt() + ctx.localVariableOffset(scope.calcOnState { lastEnteredMethod })
    }
}
