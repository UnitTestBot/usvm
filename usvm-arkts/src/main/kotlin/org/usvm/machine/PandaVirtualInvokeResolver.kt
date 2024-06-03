package org.usvm.machine

import io.ksmt.utils.asExpr
import org.jacodb.panda.dynamic.api.PandaClassTypeImpl
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.USymbolicHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isStaticHeapRef
import org.usvm.machine.state.PandaState
import org.usvm.memory.foldHeapRef
import org.usvm.model.UModelBase
import org.usvm.types.UTypeStream
import org.usvm.types.single

fun resolveVirtualInvoke(
    ctx: PandaContext,
    methodCall: PandaVirtualMethodCallInst,
    scope: PandaStepScope,
    typeSelector: PandaTypeSelector,
    forkOnRemainingTypes: Boolean,
) {
    val models = scope.calcOnState { models }

    if (models.isEmpty()) {
        resolveVirtualInvokeWithoutModel(
            ctx,
            methodCall,
            scope,
            typeSelector,
            forkOnRemainingTypes
        )
    } else {
        resolveVirtualInvokeWithModel(
            ctx,
            methodCall,
            scope,
            models.first(),
            typeSelector,
            forkOnRemainingTypes
        )
    }
}

private fun resolveVirtualInvokeWithModel(
    ctx: PandaContext,
    methodCall: PandaVirtualMethodCallInst,
    scope: PandaStepScope,
    model: UModelBase<PandaType>,
    typeSelector: PandaTypeSelector,
    forkOnRemainingTypes: Boolean,
): Unit = with(methodCall) {
    val instance = arguments.first().asExpr(ctx.addressSort)
    val concreteRef = model.eval(instance) as UConcreteHeapRef

    if (isAllocatedConcreteHeapRef(concreteRef) || isStaticHeapRef(concreteRef)) {
        val concreteInvokes = prepareVirtualInvokeOnConcreteRef(
            scope,
            concreteRef,
            ctx,
            methodCall,
            condition = ctx.trueExpr
        )

        scope.forkMulti(concreteInvokes)


        return
    }

    val typeStream = model.typeStreamOf(concreteRef)
    val typeConstraintsWithBlockOnStates = makeConcreteCallsForPossibleTypes(
        scope,
        methodCall,
        typeStream,
        typeSelector,
        ctx,
        ctx.trueExpr,
        forkOnRemainingTypes
    )

    scope.forkMulti(typeConstraintsWithBlockOnStates)
}

private fun PandaVirtualMethodCallInst.prepareVirtualInvokeOnConcreteRef(
    scope: PandaStepScope,
    concreteRef: UConcreteHeapRef,
    ctx: PandaContext,
    methodCall: PandaVirtualMethodCallInst,
    condition: UBoolExpr,
): List<Pair<UBoolExpr, (PandaState) -> Unit>> {
    // We have only one type for allocated and static heap refs
    val type = scope.calcOnState { memory.typeStreamOf(concreteRef) }.single()

    val state = { state: PandaState ->
        val concreteCall = makeConcreteMethodCall(methodCall, type)
        state.newStmt(concreteCall)
    }

    return listOf(condition to state)
}

private fun resolveVirtualInvokeWithoutModel(
    ctx: PandaContext,
    methodCall: PandaVirtualMethodCallInst,
    scope: PandaStepScope,
    typeSelector: PandaTypeSelector,
    forkOnRemainingTypes: Boolean,
): Unit = with(methodCall) {
    val instance = arguments.first().asExpr(ctx.addressSort)

    val refsWithConditions = mutableListOf<Pair<UHeapRef, UBoolExpr>>()
    foldHeapRef(
        instance,
        Unit,
        initialGuard = ctx.trueExpr,
        ignoreNullRefs = true,
        collapseHeapRefs = false,
        staticIsConcrete = true,
        blockOnConcrete = { _, (ref, condition) ->
            refsWithConditions += ref to condition
        },
        blockOnSymbolic = { _, (ref, condition) ->
            // Resolved lambda call site can't be a symbolic ref
            refsWithConditions.also { it += ref to condition }
        },
    )

    val conditionsWithBlocks = refsWithConditions.flatMapTo(mutableListOf()) { (ref, condition) ->
        when {
            isAllocatedConcreteHeapRef(ref) || isStaticHeapRef(ref) -> {
                prepareVirtualInvokeOnConcreteRef(scope, ref, ctx, methodCall, condition)
            }
            ref is USymbolicHeapRef -> {
                val state = scope.calcOnState { this }
                val typeStream = state.pathConstraints
                    .typeConstraints
                    .getTypeStream(ref)
                    // NOTE: this filter is required in case this state is actually unsat and/or
                    // does not have type constraints for this symbolic ref
                    .filterBySupertype(PandaClassTypeImpl(methodCall.method.enclosingClass.name))

                makeConcreteCallsForPossibleTypes(
                    scope,
                    methodCall,
                    typeStream,
                    typeSelector,
                    ctx,
                    condition,
                    forkOnRemainingTypes
                )
            }
            else -> error("Unexpected ref $ref")
        }
    }

    scope.forkMulti(conditionsWithBlocks)
}

private fun PandaVirtualMethodCallInst.makeConcreteMethodCall(
    methodCall: PandaVirtualMethodCallInst,
    type: PandaType,
): PandaConcreteMethodCallInst {
//    val concreteMethod = type.findMethod(method)
//        ?: error("Can't find method $method in type ${type.typeName}")
//
//    methodCall.method

    return methodCall.toConcreteMethodCall(methodCall.method)
}

private fun PandaVirtualMethodCallInst.makeConcreteCallsForPossibleTypes(
    scope: PandaStepScope,
    methodCall: PandaVirtualMethodCallInst,
    typeStream: UTypeStream<PandaType>,
    typeSelector: PandaTypeSelector,
    ctx: PandaContext,
    condition: UBoolExpr,
    forkOnRemainingTypes: Boolean,
): MutableList<Pair<UBoolExpr, (PandaState) -> Unit>> {
    val state = scope.calcOnState { this }
    state.ctx.mkBool(true)
    val inheritors = typeSelector.choose(method, typeStream)
//    val typeConstraints = inheritors.map { type ->
//        state.memory.types.evalTypeEquals(instance, type)
//    }
    val typeConstraints = emptyList<UBoolExpr>()

    val typeConstraintsWithBlockOnStates = mutableListOf<Pair<UBoolExpr, (PandaState) -> Unit>>()

    inheritors.mapIndexedTo(typeConstraintsWithBlockOnStates) { idx, type ->
        val isExpr = state.ctx.mkBool(true)

        val block = { newState: PandaState ->
//            val concreteMethod = type.findMethod(method)
//                ?: error("Can't find method $method in type ${type.typeName}")

            val concreteCall = methodCall.toConcreteMethodCall(methodCall.method)
            newState.newStmt(concreteCall)
        }

        with(ctx) {
            (condition and isExpr) to block
        }
    }

    if (forkOnRemainingTypes) {
        val excludeAllTypesConstraint = ctx.mkAnd(typeConstraints.map { ctx.mkNot(it) })
        typeConstraintsWithBlockOnStates += excludeAllTypesConstraint to { } // do nothing, just exclude types
    }

    return typeConstraintsWithBlockOnStates
}
