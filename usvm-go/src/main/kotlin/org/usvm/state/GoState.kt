package org.usvm.state

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KConst
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoAllocExpr
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoFreeVar
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoParameter
import org.jacodb.go.api.GoType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SliceType
import org.usvm.GoCall
import org.usvm.GoContext
import org.usvm.GoTarget
import org.usvm.PathNode
import org.usvm.UBoolSort
import org.usvm.UCallStack
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.ULValuePointer
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.allocateArray
import org.usvm.api.allocateArrayInitialized
import org.usvm.api.readField
import org.usvm.api.writeArrayLength
import org.usvm.api.writeField
import org.usvm.collection.field.UFieldLValue
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.merging.MutableMergeGuard
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sampleUValue
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.type.GoBasicTypes
import org.usvm.type.underlying

class GoState(
    ctx: GoContext,
    ownership: MutabilityOwnership,
    override val entrypoint: GoMethod,
    callStack: UCallStack<GoMethod, GoInst> = UCallStack(),
    pathConstraints: UPathConstraints<GoType> = UPathConstraints(ctx, ownership),
    memory: UMemory<GoType, GoMethod> = UMemory(ctx, ownership, pathConstraints.typeConstraints),
    models: List<UModelBase<GoType>> = listOf(),
    pathNode: PathNode<GoInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<GoInst>> = PathNode.root(),
    targets: UTargetsSet<GoTarget, GoInst> = UTargetsSet.empty(),
    var methodResult: GoMethodResult = GoMethodResult.NoCall,
    var data: GoStateData = GoStateData()
) : UState<GoType, GoMethod, GoInst, GoContext, GoTarget, GoState>(
    ctx,
    ownership,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    targets
) {
    override fun clone(newConstraints: UPathConstraints<GoType>?): GoState {
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val clonedConstraints = newConstraints?.also {
            this.pathConstraints.changeOwnership(newThisOwnership)
            it.changeOwnership(cloneOwnership)
        } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        this.ownership = newThisOwnership
        return GoState(
            ctx,
            ownership,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints, newThisOwnership, cloneOwnership),
            models,
            pathNode,
            forkPoints,
            targets.clone(),
            methodResult,
            data.clone()
        )
    }

    override fun mergeWith(other: GoState, by: Unit): GoState? {
        val newThisOwnership = MutabilityOwnership()
        val newOtherOwnership = MutabilityOwnership()
        val mergedOwnership = MutabilityOwnership()

        require(entrypoint == other.entrypoint) { "Cannot merge states with different entrypoints" }
        // TODO: copy-paste

        val mergedPathNode = pathNode.mergeWith(other.pathNode, Unit) ?: return null
        val mergedForkPoints = forkPoints.mergeWith(other.forkPoints, Unit) ?: return null

        val mergeGuard = MutableMergeGuard(ctx)
        val mergedCallStack = callStack.mergeWith(other.callStack, Unit) ?: return null
        val mergedPathConstraints = pathConstraints.mergeWith(
            other.pathConstraints, mergeGuard, newThisOwnership, newOtherOwnership, mergedOwnership
        ) ?: return null
        val mergedMemory =
            memory.clone(mergedPathConstraints.typeConstraints, newThisOwnership, newOtherOwnership)
                .mergeWith(other.memory, mergeGuard, newThisOwnership, newOtherOwnership, mergedOwnership)
                ?: return null
        val mergedModels = models + other.models
        val methodResult = if (other.methodResult == GoMethodResult.NoCall && methodResult == GoMethodResult.NoCall) {
            GoMethodResult.NoCall
        } else {
            return null
        }
        val mergedTargets = targets.takeIf { it == other.targets } ?: return null
        mergedPathConstraints += ctx.mkOr(mergeGuard.thisConstraint, mergeGuard.otherConstraint)

        val mergedData = data.mergeWith(other.data)

        return GoState(
            ctx,
            mergedOwnership,
            entrypoint,
            mergedCallStack,
            mergedPathConstraints,
            mergedMemory,
            mergedModels,
            mergedPathNode,
            mergedForkPoints,
            mergedTargets,
            methodResult,
            mergedData
        )
    }

    override val isExceptional: Boolean get() = methodResult is GoMethodResult.Panic

    override fun toString(): String = buildString {
        appendLine("Instruction: $currentStatement")
        if (isExceptional) appendLine("Exception: $methodResult")
        appendLine(callStack)
    }

    fun newInst(inst: GoInst) {
        pathNode += inst
    }

    fun returnValue(valueToReturn: UExpr<out USort>, type: GoType) {
        val returnFromMethod = lastEnteredMethod
        val returnSite = callStack.pop()
        if (callStack.isNotEmpty()) {
            memory.stack.pop()
        }

        if (!isExceptional) {
            methodResult = GoMethodResult.Success(valueToReturn, returnFromMethod, type)
        }

        if (returnSite != null) {
            newInst(returnSite)
        }

        data.flowStack.removeLast()
    }

    fun handlePanic() {
        require(methodResult is GoMethodResult.Panic)

        val returnSite = callStack.pop()
        if (callStack.isNotEmpty()) {
            memory.stack.pop()
        }

        if (returnSite != null) {
            newInst(returnSite)
        }
    }

    fun panic(expr: UExpr<out USort>, type: GoType) {
        methodResult = GoMethodResult.Panic(expr.cast(), type)
        data.flowStack.add(GoFlowStatus.PANIC)
    }

    fun panic(text: String) = panic(mkString(text), GoBasicTypes.STRING)

    fun recover(): UExpr<out USort> = with(ctx) {
        if (methodResult is GoMethodResult.Panic) {
            val result = (methodResult as GoMethodResult.Panic).value
            methodResult = GoMethodResult.NoCall
            return result
        }
        return nullRef
    }

    fun runDefers() {
        data.flowStack.add(GoFlowStatus.DEFER)
    }

    fun addCall(call: GoCall, returnInst: GoInst? = null) = with(ctx) {
        val methodInfo = getMethodInfo(call.method)
        val freeVariables = mutableListOf<UExpr<out USort>>().also {
            if (call.method is GoFunction) {
                call.method.freeVars.forEach { variable -> it.add(findParam(variable)) }
            }
        }
        val parameters = mutableListOf<GoParameter>().also {
            if (call.method is GoFunction) {
                it.addAll(call.method.parameters)
            }
        }

        data.flowStack.add(GoFlowStatus.NORMAL)
        callStack.push(call.method, returnInst)
        if (methodInfo.arguments.isEmpty()) {
            memory.stack.push(methodInfo.argumentsCount, methodInfo.variablesCount)
        } else {
            memory.stack.push(methodInfo.arguments, methodInfo.variablesCount)
        }

        parameters.forEachIndexed { i, parameter ->
            when (val type = parameter.type) {
                is ArrayType -> {
                    val ref = memory.read(URegisterStackLValue(addressSort, i)).asExpr(addressSort)
                    memory.writeArrayLength(ref, mkSizeExpr(type.len.toInt()), type, sizeSort)
                }
            }
        }

        freeVariables.forEachIndexed { i, variable ->
            val lvalue = URegisterStackLValue(variable.sort, i + freeVariableOffset(call.method))
            memory.write(lvalue, variable.asExpr(variable.sort), trueExpr)
        }

        newInst(call.entrypoint)
    }

    fun deref(pointer: UHeapRef, sort: USort): UExpr<out USort> {
        val lvaluePointer = tryLValuePointer(pointer)
        if (lvaluePointer != null) {
            return memory.read(lvaluePointer.lvalue)
        }

        val index = 0
        return memory.readField(pointer, index, sort)
    }

    fun store(pointer: UHeapRef, rvalue: UExpr<out USort>) {
        val lvaluePointer = tryLValuePointer(pointer)
        if (lvaluePointer != null) {
            memory.write(lvaluePointer.lvalue.withSort(rvalue.sort), rvalue.asExpr(rvalue.sort), ctx.trueExpr)
            return
        }

        val index = 0
        memory.writeField(pointer, index, rvalue.sort, rvalue.asExpr(rvalue.sort), ctx.trueExpr)
    }

    fun isPointer(pointer: UHeapRef): UExpr<UBoolSort> {
        val index = 1
        val field = memory.readField(pointer, index, ctx.bv32Sort)
        return ctx.mkEq(field, ctx.mkBv(POINTER_FIELD, ctx.bv32Sort))
    }

    fun isBoxed(ref: UHeapRef): UExpr<UBoolSort> {
        val index = 1
        val field = memory.readField(ref, index, ctx.bv32Sort)
        return ctx.mkEq(field, ctx.mkBv(BOXED_VALUE_FIELD, ctx.bv32Sort))
    }

    fun isBoxedConcrete(ref: UHeapRef): Boolean {
        val index = 1
        val field = memory.readField(ref, index, ctx.bv32Sort)
        return field is KBitVec32Value && field.intValue == BOXED_VALUE_FIELD
    }

    fun box(expr: UExpr<out USort>, targetType: GoType): UHeapRef {
        if (expr.sort == ctx.addressSort && expr !is UNullRef && expr !is KConst<*> && isBoxedConcrete(expr.asExpr(ctx.addressSort))) {
            return box(unbox(expr.asExpr(ctx.addressSort), ctx.typeToSort(targetType.underlying())), targetType)
        }

        return mkTuple(targetType, expr, ctx.mkBv(BOXED_VALUE_FIELD, ctx.bv32Sort))
    }

    fun unbox(expr: UHeapRef, sort: USort): UExpr<out USort> {
        val index = 0
        return memory.readField(expr, index, sort)
    }

    fun mkPointer(type: GoType): UConcreteHeapRef {
        return mkPointer(type, sampleValue(type))
    }

    fun mkPointer(type: GoType, expr: UExpr<out USort>): UConcreteHeapRef {
        return mkTuple(type, expr, ctx.mkBv(POINTER_FIELD, ctx.bv32Sort))
    }

    fun mkPointer(type: GoType, lvalue: ULValue<*, *>): UExpr<out USort> {
        return mkPointer(type, ULValuePointer(ctx, lvalue))
    }

    fun mkTuple(type: GoType, vararg fields: UExpr<out USort>): UConcreteHeapRef = with(ctx) {
        val ref = memory.allocConcrete(type)
        for ((index, field) in fields.withIndex()) {
            memory.write(UFieldLValue(field.sort, ref, index), field.asExpr(field.sort), trueExpr)
        }
        return ref
    }

    fun mkString(value: String): UExpr<out USort> {
        return memory.allocateArrayInitialized(
            GoBasicTypes.STRING,
            ctx.bv8Sort,
            ctx.sizeSort,
            value.toByteArray().map { ctx.mkBv(it) }.asSequence()
        )
    }

    private fun sampleValue(type: GoType): UExpr<out USort> = when (type) {
        is ArrayType -> {
            val sort = ctx.typeToSort(type.elementType)
            val contents = Array(type.len.toInt()) { sampleValue(type.elementType).asExpr(sort) }.asSequence()
            memory.allocateArrayInitialized(type, sort, ctx.sizeSort, contents)
        }

        is SliceType -> memory.allocateArray(type, ctx.sizeSort, ctx.mkSizeExpr(0))
        is BasicType -> ctx.typeToSort(type).sampleUValue()
        is NamedType -> box(sampleValue(type.underlyingType), type)
        else -> memory.allocConcrete(type)
    }

    private fun tryLValuePointer(pointer: UHeapRef): ULValuePointer? {
        val index = 0
        val payload = memory.readField(pointer, index, ctx.addressSort)
        if (payload is ULValuePointer) {
            return payload
        }
        return null
    }

    private fun findParam(freeVar: GoFreeVar): UExpr<out USort> {
        val stack = callStack.clone()
        val registers = memory.stack.clone()
        while (!stack.isEmpty()) {
            val param = findParam(stack.lastMethod(), freeVar)
            if (param != null) {
                return registers.read(URegisterStackLValue(ctx.typeToSort(param.first), param.second))
            }

            stack.pop()
            registers.pop()
        }


        throw IllegalStateException("param not found")
    }

    private fun findParam(method: GoMethod, freeVar: GoFreeVar): Pair<GoType, Int>? {
        val param = method.parameters.filterIsInstance<GoParameter>().find { it.name == freeVar.name }
        if (param != null) {
            return Pair(PointerType(param.type), param.index + ctx.localVariableOffset(method))
        }

        val assign = method.blocks.flatMap { it.instructions }.filterIsInstance<GoAssignInst>().find {
            it.rhv is GoAllocExpr && (it.rhv as GoAllocExpr).comment == freeVar.name
        }
        if (assign != null) {
            val alloc = assign.rhv as GoAllocExpr
            return Pair(PointerType(alloc.type), index(method, alloc.name))
        }

        return null
    }

    private fun index(method: GoMethod, name: String): Int {
        return name.substring(1).toInt() + ctx.localVariableOffset(method)
    }

    private fun <T : USort> ULValue<*, *>.withSort(sort: T): ULValue<*, T> {
        check(this@withSort.sort == sort) { "Sort mismatch" }

        @Suppress("UNCHECKED_CAST")
        return this@withSort as ULValue<*, T>
    }

    companion object {
        const val POINTER_FIELD = 50541
        const val BOXED_VALUE_FIELD = 80085
    }
}