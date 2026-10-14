package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.UExpr
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.interpreter.getGlobals

/** Recognize the implicit builtin, excluding source bindings with the same lib declaration type. */
internal fun TsExprResolver.isBuiltInStringConverter(expr: EtsPtrCallExpr): Boolean {
    val builtinCallSite = expr.callee.name == "String" &&
        expr.callee.enclosingClass == EtsClassSignature.UNKNOWN
    if (!builtinCallSite) return false

    val method = scope.calcOnState { lastEnteredMethod }
    val assignments = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
    val visited = mutableSetOf<EtsLocal>()
    var pointer = expr.ptr
    while (pointer.name.startsWith("%") && visited.add(pointer)) {
        val definition = assignments.singleOrNull { it.lhv == pointer } ?: return false
        pointer = definition.rhv as? EtsLocal ?: return false
    }
    if (pointer.name != "String") return false

    val signature = (expr.ptr.type as? EtsFunctionType)?.signature ?: return false
    val parameter = signature.parameters.singleOrNull() ?: return false
    val builtinSignature = signature.enclosingClass == EtsClassSignature.UNKNOWN &&
        signature.name.isEmpty() && signature.returnType == EtsStringType &&
        parameter.type == EtsAnyType && parameter.isOptional && !parameter.isRest
    if (!builtinSignature) return false

    val file = method.enclosingClass?.declaringFile ?: return false
    val sourceBinding = method.parameters.any { it.name == pointer.name } ||
        method.getDeclaredLocals().any { it.name == pointer.name } ||
        file.getGlobals().any { it.name == pointer.name } ||
        file.importInfos.any { it.name == pointer.name }

    return !sourceBinding
}

/** String identity and existing exact primitive conversions; broader ToPrimitive remains explicit. */
internal fun TsExprResolver.handleStringConverter(args: List<EtsValue>): UExpr<*>? = with(ctx) {
    val arguments = buildList {
        for (argument in args) {
            val value = resolve(argument) ?: return null
            add(value)
        }
    }
    if (arguments.isEmpty()) return mkStringConstant("", scope)

    val input = arguments.first()
    val value = if (input.isFakeObject()) {
        val payload = input.extractSingleValueFromFakeObjectOrNull(scope)
        if (payload == null) {
            stopRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.STRING_PRIMITIVE_CONVERSION,
                detail = "String conversion of a mixed runtime kind requires a ToPrimitive model",
            )
            return null
        }
        payload
    } else {
        input
    }

    val constant = concreteStringValue(value)
    if (constant != null) return mkStringConstant(constant, scope)

    if (value.sort == boolSort) {
        val text = mkIte(value.asExpr(boolSort), mkStringConstant("true", scope), mkStringConstant("false", scope))
        return resolveHeapRef(scope, text)
    }
    if (value.sort == addressSort) {
        val reference = resolveHeapRef(scope, value.asExpr(addressSort)) ?: return null
        val referenceConstant = concreteStringValue(reference)
        if (referenceConstant != null) return mkStringConstant(referenceConstant, scope)

        val hasStringBacking = scope.calcOnState { reference in boundedStringBackingRefs }
        if (hasStringBacking) return reference
    }

    stopRuntimeFeatureLimitation(
        reason = TsRuntimeFeatureLimitationReason.STRING_PRIMITIVE_CONVERSION,
        detail = "String conversion requires a modeled string or exact primitive: $value",
    )
    null
}
