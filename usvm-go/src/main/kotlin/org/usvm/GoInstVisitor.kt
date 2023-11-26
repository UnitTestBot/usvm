package org.usvm

import io.ksmt.expr.KConst
import io.ksmt.utils.asExpr
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
import org.jacodb.go.api.NullType
import org.jacodb.go.api.TupleType
import org.usvm.api.collection.ObjectMapCollectionApi.ensureObjectMapSizeCorrect
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.interpreter.GoStepScope
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.statistics.ApplicationGraph
import org.usvm.type.underlying

class GoInstVisitor(
    private val ctx: GoContext,
    private val program: GoProgram,
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
            when (inst.returnValues.size) {
                0 -> returnValue(ctx.voidValue, NullType())
                1 -> returnValue(inst.returnValues[0].accept(exprVisitor), inst.returnValues[0].type)
                else -> {
                    val type = TupleType(inst.returnValues.map { it.type })
                    returnValue(mkTuple(type, fields = inst.returnValues.map { it.accept(exprVisitor) }.toTypedArray()), type)
                }
            }
        }
        return GoNullInst(inst.location.method)
    }

    override fun visitGoRunDefersInst(inst: GoRunDefersInst): GoInst {
        return scope.calcOnState {
            runDefers()
            next(inst)
        }
    }

    override fun visitGoPanicInst(inst: GoPanicInst): GoInst {
        val value = inst.throwable.accept(exprVisitor)

        return scope.calcOnState {
            panic(value, inst.throwable.type)
            next(inst)
        }
    }

    override fun visitGoGoInst(inst: GoGoInst): GoInst {
        return unsupportedInst("Go")
    }

    override fun visitGoDeferInst(inst: GoDeferInst): GoInst {
        val name = (inst.func.accept(exprVisitor) as KConst).toString()
        val method = program.findMethod(inst.location, name)

        val parameters = inst.args.map { it.accept(exprVisitor) }.toTypedArray()
        val call = GoCall(method, applicationGraph.entryPoints(method).first())
        ctx.setMethodInfo(method, parameters)

        scope.doWithState {
            data.addDeferredCall(lastEnteredMethod, call)
        }
        return next(inst)
    }

    override fun visitGoSendInst(inst: GoSendInst): GoInst {
        return unsupportedInst("Send")
    }

    override fun visitGoStoreInst(inst: GoStoreInst): GoInst {
        val pointer = inst.lhv.accept(exprVisitor).asExpr(ctx.addressSort)
        if (pointer is UNullRef) {
            return scope.calcOnState {
                panic("null pointer dereference")
                next(inst)
            }
        }
        val rvalue = inst.rhv.accept(exprVisitor)
        scope.doWithState {
            store(pointer, rvalue)
        }

        return next(inst)
    }

    override fun visitGoMapUpdateInst(inst: GoMapUpdateInst): GoInst {
        val map = exprVisitor.unboxNamedRef(inst.map.accept(exprVisitor).asExpr(ctx.addressSort), inst.map.type)
        val type = inst.map.type.underlying()
        val key = inst.key.accept(exprVisitor)
        val value = inst.value.accept(exprVisitor)
        val isRefKey = key.sort == ctx.addressSort

        exprVisitor.checkNotNull(map) ?: throw IllegalStateException()
        scope.ensureObjectMapSizeCorrect(map, type) ?: throw IllegalStateException()

        scope.doWithState {
            val mapContainsLValue = if (isRefKey) {
                URefSetEntryLValue(map, key.asExpr(ctx.addressSort), type)
            } else {
                USetEntryLValue(key.sort, map, key.asExpr(key.sort), type, USizeExprKeyInfo())
            }
            val mapEntryLValue = if (isRefKey) {
                URefMapEntryLValue(value.sort, map, key.asExpr(ctx.addressSort), type)
            } else {
                UMapEntryLValue(key.sort, value.sort, map, key.asExpr(key.sort), type, USizeExprKeyInfo())
            }
            val currentSize = symbolicObjectMapSize(map, type)

            memory.write(mapEntryLValue, value.asExpr(value.sort), ctx.trueExpr)
            memory.write(mapContainsLValue, ctx.trueExpr, ctx.trueExpr)

            val updatedSize = ctx.mkSizeAddExpr(currentSize, ctx.mkSizeExpr(1))
            memory.write(UMapLengthLValue(map, type, ctx.sizeSort), updatedSize, ctx.mkNot(memory.read(mapContainsLValue)))
        }

        return next(inst)
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

        if (rvalue == ctx.noValue) {
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
