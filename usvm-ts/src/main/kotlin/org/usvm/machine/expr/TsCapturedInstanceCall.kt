package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRequireObjectCoercibleExpr
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.utils.callExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isTrue
import org.usvm.machine.call.isBuiltinArrayConstructor
import org.usvm.machine.call.isBuiltinDateGlobalType
import org.usvm.machine.call.isBuiltinGlobalType
import org.usvm.machine.runtimeFunctionType
import org.usvm.machine.state.lastStmt
import org.usvm.util.getAllFields
import org.usvm.util.mkFieldLValue

/** A method property selected before evaluating call arguments. */
class TsCapturedInstanceCall(
    val receiver: UExpr<*>,
    val sourceReceiver: EtsLocal,
    /** Extracted native methods retain their owner/name; each invocation keeps its own argument and result types. */
    val nativeCallee: EtsMethodSignature? = null,
)

internal fun TsExprResolver.tryCaptureInstanceCall(
    field: EtsInstanceFieldRef,
): TsExprApproximationResult = with(ctx) {
    when (val native = tryCaptureNativeObjectAssign(field)) {
        TsExprApproximationResult.NoApproximation -> {}
        else -> return native
    }

    val method = scope.calcOnState { lastEnteredMethod }
    val assignment = scope.calcOnState { lastStmt } as? EtsAssignStmt
        ?: return TsExprApproximationResult.NoApproximation
    val target = assignment.lhv as? EtsLocal ?: return TsExprApproximationResult.NoApproximation
    val call = method.cfg.stmts.asSequence().mapNotNull { it.callExpr as? EtsPtrCallExpr }
        .firstOrNull { it.ptr == target && it.receiver == field.instance && it.callee.name == field.field.name }
        ?: return TsExprApproximationResult.NoApproximation

    // Actual callback fields keep their value, including non-callable values.
    val owners = hierarchy.classesForType(EtsClassType(field.field.enclosingClass))
    val hasDeclaredField = owners.any { owner ->
        owner.getAllFields(hierarchy).any { it.name == field.field.name }
    }

    if (hasDeclaredField) {
        return TsExprApproximationResult.NoApproximation
    }

    val priorAssignments = method.cfg.stmts.takeWhile { it !== assignment }.filterIsInstance<EtsAssignStmt>()
    val source = originalReceiver(requireNotNull(call.receiver), priorAssignments)
    val receiver = resolve(field.instance) ?: return TsExprApproximationResult.ResolveFailure
    val implicitGlobal = isBuiltinArrayConstructor(receiver) || isImplicitModeledGlobal(source, call.callee, method)
    val reference = when {
        implicitGlobal -> null
        receiver.isFakeObject() -> receiver.extractRef(scope)
        receiver.sort == addressSort -> receiver.asExpr(addressSort)
        else -> null
    }
    val builtinReturn = field.field.name == "return" && reference is UConcreteHeapRef &&
        scope.calcOnState { arrayIterators[reference] != null || stringIterators[reference] != null }
    if (builtinReturn) return TsExprApproximationResult.SuccessfulApproximation(mkUndefinedValue())

    if (reference != null) {
        if (!ensureSupportedArrayPrototypeAccess(reference, field.field.name)) {
            return TsExprApproximationResult.ResolveFailure
        }
        val referenceGuard = if (receiver.isFakeObject()) receiver.getFakeType(scope).refTypeExpr else trueExpr
        val valid = mkOr(mkNot(referenceGuard), mkNotNullOrUndefined(reference))
        scope.fork(valid, blockOnFalseState = {
            throwException("Undefined or null property access: ${field.field.name} of $reference")
        }) ?: return TsExprApproximationResult.ResolveFailure

        val stored = if (referenceGuard.isTrue) {
            scope.calcOnState { memory.read(mkFieldLValue(addressSort, reference, field.field)) }
        } else {
            null
        }
        if (stored != null && isAllocatedConcreteHeapRef(stored)) {
            return TsExprApproximationResult.SuccessfulApproximation(stored)
        }
    }

    // Logger's compatibility approximation deliberately matches the source variable name.
    val approximationReceiver = if (implicitGlobal || source.name == "Logger") source else field.instance
    val pointer = scope.calcOnState { memory.allocConcrete(scene.runtimeFunctionType()) }
    scope.doWithState {
        capturedInstanceCalls = capturedInstanceCalls.put(
            pointer,
            TsCapturedInstanceCall(
                receiver = receiver,
                sourceReceiver = approximationReceiver,
            ),
            ownership,
        )
    }
    TsExprApproximationResult.SuccessfulApproximation(pointer)
}

internal fun TsExprResolver.handleCapturedInstanceCall(
    call: EtsPtrCallExpr,
    captured: TsCapturedInstanceCall,
): UExpr<out USort>? = with(ctx) {
    val nativeCallee = captured.nativeCallee
    val receiver = if (nativeCallee != null) captured.sourceReceiver else requireNotNull(call.receiver)
    val callee = if (nativeCallee != null) {
        call.callee.copy(enclosingClass = nativeCallee.enclosingClass, name = nativeCallee.name)
    } else {
        call.callee
    }
    val instanceCall = EtsInstanceCallExpr(
        instance = receiver,
        callee = callee,
        args = call.args,
        type = call.type,
    )
    val approximationCall = instanceCall.copy(instance = captured.sourceReceiver)
    when (val approximation = tryApproximateGlobalInstanceCall(approximationCall)) {
        is TsExprApproximationResult.SuccessfulApproximation -> return approximation.expr
        TsExprApproximationResult.ResolveFailure -> return null
        TsExprApproximationResult.NoApproximation -> {}
    }

    val args = mutableListOf<UExpr<*>>()
    for (argument in call.args) {
        val resolved = resolve(argument) ?: return null
        args += resolved
    }
    callInstanceMethod(
        scope = scope,
        call = instanceCall,
        instance = captured.receiver,
        args = args,
        originalCall = call.copy(callee = callee, receiver = captured.sourceReceiver),
        targetCaptured = true,
    )
}

internal fun TsExprResolver.resolveCallPointer(local: EtsLocal): UExpr<out USort>? = with(ctx) {
    val value = resolve(local) ?: return null
    if (!value.isFakeObject()) return value

    val reference = value.extractRef(scope)
    val knownFunction = reference is UConcreteHeapRef && scope.calcOnState {
        associatedFunction[reference] != null || capturedInstanceCalls[reference] != null
    }
    if (!knownFunction) return value

    scope.assert(value.getFakeType(scope).refTypeExpr) ?: return null
    reference
}

private fun originalReceiver(local: EtsLocal, assignments: List<EtsAssignStmt>): EtsLocal {
    val visited = mutableSetOf<EtsLocal>()
    var current = local
    while (current.name.startsWith("%") && visited.add(current)) {
        val definition = assignments.lastOrNull { it.lhv == current } ?: return current
        val source = when (val value = definition.rhv) {
            is EtsLocal -> value
            is EtsRequireObjectCoercibleExpr -> value.arg as? EtsLocal
            else -> null
        } ?: return current
        current = source
    }
    return if (current in visited) local else current
}

/** Builtin objects handled by existing approximations have no input heap object to dereference. */
private fun isImplicitModeledGlobal(source: EtsLocal, callee: EtsMethodSignature, method: EtsMethod): Boolean {
    if (source.type.isBuiltinGlobalType(expectedName = "Math") ||
        source.type.isBuiltinGlobalType(expectedName = "Number")
    ) {
        return true
    }

    if (source.type.isBuiltinDateGlobalType()) {
        return true
    }

    val modeled = when (source.name) {
        "Logger" -> true
        "Number" -> callee.name == "isNaN"
        "Promise" -> callee.name in listOf("resolve", "reject")
        "Math" -> callee.name == "floor"
        else -> false
    }
    val globalType = source.type is EtsUnclearRefType ||
        (source.name == "Number" && source.type is EtsFunctionType)
    if (!modeled || !globalType) return false

    return method.parameters.none { it.name == source.name } &&
        method.cfg.stmts.filterIsInstance<EtsAssignStmt>().none { it.lhv == source }
}
