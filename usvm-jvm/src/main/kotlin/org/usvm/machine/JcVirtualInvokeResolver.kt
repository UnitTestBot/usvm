package org.usvm.machine

import io.ksmt.expr.KExpr
import io.ksmt.utils.asExpr
import org.jacodb.api.JcType
import org.jacodb.api.ext.toType
import org.usvm.NoSolverStateForker
import org.usvm.StateForker
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.USymbolicHeapRef
import org.usvm.WithSolverStateForker
import org.usvm.api.evalTypeEquals
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isStaticHeapRef
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.interpreter.JcTypeSelector
import org.usvm.machine.state.JcState
import org.usvm.machine.state.newStmt
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.types.UTypeStream
import org.usvm.types.first
import org.usvm.util.findMethod

/**
 * Resolves a virtual [methodCall] with different strategies for forks - with solver or not.
 */
fun StateForker.resolveVirtualInvoke(
    ctx: JcContext,
    methodCall: JcVirtualMethodCallInst,
    scope: JcStepScope,
    typeSelector: JcTypeSelector,
    forkOnRemainingTypes: Boolean,
) {
    when (this) {
        WithSolverStateForker -> resolveVirtualInvokeWithSolver(
            ctx,
            methodCall,
            scope,
            typeSelector,
            forkOnRemainingTypes
        )
        NoSolverStateForker -> resolveVirtualInvokeWithoutSolver(
            ctx,
            methodCall,
            scope,
            typeSelector,
            forkOnRemainingTypes
        )
    }
}

private fun resolveVirtualInvokeWithSolver(
    ctx: JcContext,
    methodCall: JcVirtualMethodCallInst,
    scope: JcStepScope,
    typeSelector: JcTypeSelector,
    forkOnRemainingTypes: Boolean,
): Unit = with(methodCall) {
    val instance = arguments.first().asExpr(ctx.addressSort)
    val concreteRef = scope.calcOnState { models.first().eval(instance) } as UConcreteHeapRef

    if (isAllocatedConcreteHeapRef(concreteRef) || isStaticHeapRef(concreteRef)) {
        val concreteCall = makeConcreteMethodCall(scope, concreteRef, methodCall)
        scope.doWithState {
            newStmt(concreteCall)
        }

        return@with
    }

    val typeStream = scope.calcOnState { models.first().typeStreamOf(concreteRef) }
    val typeConstraintsWithBlockOnStates = makeConcreteCallsForPossibleTypes(
        scope,
        methodCall,
        typeStream,
        typeSelector,
        instance,
        ctx,
        ctx.trueExpr,
        forkOnRemainingTypes
    )
    scope.forkMulti(typeConstraintsWithBlockOnStates)
}

private fun resolveVirtualInvokeWithoutSolver(
    ctx: JcContext,
    methodCall: JcVirtualMethodCallInst,
    scope: JcStepScope,
    typeSelector: JcTypeSelector,
    forkOnRemainingTypes: Boolean,
): Unit = with(methodCall) {
    val instance = arguments.first().asExpr(ctx.addressSort)

    val refsWithConditions = mutableListOf<Pair<UHeapRef, UBoolExpr>>()
    foldHeapRefWithStaticAsSymbolic(
        instance,
        refsWithConditions,
        initialGuard = ctx.trueExpr,
        ignoreNullRefs = true,
        collapseHeapRefs = false,
        blockOnConcrete = { curRefsWithConditions, (ref, condition) -> curRefsWithConditions.also { it += ref to condition } },
        blockOnSymbolic = { curRefsWithConditions, (ref, condition) -> curRefsWithConditions.also { it += ref to condition } },
    )

    val conditionsWithBlocks = refsWithConditions.flatMap { (ref, condition) ->
        when {
            isAllocatedConcreteHeapRef(ref) || isStaticHeapRef(ref) -> {
                val concreteCall = makeConcreteMethodCall(scope, ref, methodCall)
                listOf(condition to { state: JcState -> state.newStmt(concreteCall) })
            }
            ref is USymbolicHeapRef -> {
                val state = scope.calcOnState { this }
                val typeStream: UTypeStream<JcType> = state.pathConstraints
                    .typeConstraints
                    .getTypeStream(ref)
                    // NOTE: this filter is required in case this state is actually unsat and/or does not have type constraints for this symbolic ref
                    .filterBySupertype(methodCall.method.enclosingClass.toType())

                makeConcreteCallsForPossibleTypes(
                    scope,
                    methodCall,
                    typeStream,
                    typeSelector,
                    instance,
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

private fun JcVirtualMethodCallInst.makeConcreteMethodCall(
    scope: JcStepScope,
    concreteRef: UConcreteHeapRef,
    methodCall: JcVirtualMethodCallInst,
): JcConcreteMethodCallInst {
    // We have only one type for allocated and static heap refs
    val type = scope.calcOnState { memory.typeStreamOf(concreteRef) }.first()

    val concreteMethod = type.findMethod(method)
        ?: error("Can't find method $method in type ${type.typeName}")

    return methodCall.toConcreteMethodCall(concreteMethod.method)
}

private fun JcVirtualMethodCallInst.makeConcreteCallsForPossibleTypes(
    scope: JcStepScope,
    methodCall: JcVirtualMethodCallInst,
    typeStream: UTypeStream<JcType>,
    typeSelector: JcTypeSelector,
    instance: KExpr<UAddressSort>,
    ctx: JcContext,
    condition: UBoolExpr,
    forkOnRemainingTypes: Boolean,
): MutableList<Pair<UBoolExpr, (JcState) -> Unit>> {
    val state = scope.calcOnState { this }
    val inheritors = typeSelector.choose(method, typeStream)
    val typeConstraints = inheritors.map { type ->
        state.memory.types.evalTypeEquals(instance, type)
    }

    val typeConstraintsWithBlockOnStates = mutableListOf<Pair<UBoolExpr, (JcState) -> Unit>>()

    inheritors.mapIndexedTo(typeConstraintsWithBlockOnStates) { idx, type ->
        val isExpr = typeConstraints[idx]

        val block = { newState: JcState ->
            val concreteMethod = type.findMethod(method)
                ?: error("Can't find method $method in type ${type.typeName}")

            val concreteCall = methodCall.toConcreteMethodCall(concreteMethod.method)
            newState.newStmt(concreteCall)
        }

        with(ctx) { (condition and isExpr) to block }
    }

    if (forkOnRemainingTypes) {
        val excludeAllTypesConstraint = ctx.mkAnd(typeConstraints.map { ctx.mkNot(it) })
        typeConstraintsWithBlockOnStates += excludeAllTypesConstraint to { } // do nothing, just exclude types
    }

    return typeConstraintsWithBlockOnStates
}
