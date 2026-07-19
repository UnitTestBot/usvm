package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.evalTypeEquals
import org.usvm.machine.expr.TsExprApproximationResult.Companion.from
import org.usvm.util.type

private val exactUnknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)

/**
 * Symbolic implementation of the exact builtin subset that the current USVM
 * heap can represent without inventing property-presence or Map state.
 * Operations outside that representable subset are rejected with a stable
 * capability reason rather than routed to an unconstrained call mock.
 */
internal fun TsExprResolver.tryApproximateExactBuiltinCall(
    expr: EtsInstanceCallExpr,
): TsExprApproximationResult? = with(ctx) {
    if (!options.exactCollectionBuiltins) return null

    if (expr.instance.name == "Array" && expr.callee.name == "isArray") {
        if (expr.args.size != 1) return rejectBuiltin(expr, "array-is-array-arity")
        val subject = resolve(expr.args.single()) ?: return TsExprApproximationResult.ResolveFailure
        return from(isArrayValue(subject))
    }

    if (expr.callee.name == "call") {
        when (findBuiltinFunctionName(expr.instance)) {
            "toString" -> {
                if (expr.args.size != 1) return rejectBuiltin(expr, "object-tag-arity")
                val subject = resolve(expr.args.single())
                    ?: return TsExprApproximationResult.ResolveFailure
                return from(objectToStringTag(subject))
            }

            "hasOwnProperty" -> return rejectBuiltin(
                expr,
                SymbolicSemanticReason.PROPERTY_PRESENCE_NOT_TRACKED.code,
                SymbolicSemanticReason.PROPERTY_PRESENCE_NOT_TRACKED,
            )
        }
    }

    if (expr.callee.name in setOf("get", "has", "set") && expr.isMapReceiver()) {
        return rejectBuiltin(
            expr,
            SymbolicSemanticReason.MAP_STATE_NOT_MATERIALIZED.code,
            SymbolicSemanticReason.MAP_STATE_NOT_MATERIALIZED,
        )
    }

    null
}

internal fun TsExprResolver.tryReadExactBuiltinField(
    field: EtsInstanceFieldRef,
): ExactSymbolicResolution {
    if (!options.exactCollectionBuiltins) {
        return ExactSymbolicResolution.NotApplicable
    }

    val fieldName = field.field.name
    val objectPrototypeField = fieldName == "prototype" && field.instance.name == "Object"
    val objectPrototypeFunction = fieldName in setOf("toString", "hasOwnProperty") &&
        isObjectPrototypeValue(field.instance)
    if (objectPrototypeField || objectPrototypeFunction) {
        val runtimeValue = scope.calcOnState { memory.allocConcrete(field.type) }
        return ExactSymbolicResolution.Resolved(runtimeValue)
    }

    if (fieldName != "size" || !field.instance.type.isMapType()) {
        return ExactSymbolicResolution.NotApplicable
    }
    recordSymbolicSemanticFallback(
        SymbolicSemanticReason.MAP_STATE_NOT_MATERIALIZED,
        field,
    )
    scope.assert(ctx.falseExpr)
    return ExactSymbolicResolution.PendingOrRejected
}

private fun TsExprResolver.isArrayValue(subject: UExpr<out USort>): UBoolExpr = with(ctx) {
    when {
        subject.isFakeObject() -> {
            val type = subject.getFakeType(scope)
            mkAnd(
                type.refTypeExpr,
                scope.calcOnState {
                    memory.types.evalIsSubtype(subject.extractRef(scope), exactUnknownArrayType)
                },
            )
        }

        subject.sort == addressSort -> scope.calcOnState {
            memory.types.evalIsSubtype(subject.asExpr(addressSort), exactUnknownArrayType)
        }

        else -> falseExpr
    }
}

private fun TsExprResolver.objectToStringTag(subject: UExpr<out USort>): UExpr<out USort> = with(ctx) {
    val arrayTag = mkStringConstant("[object Array]", scope)
    val objectTag = mkStringConstant("[object Object]", scope)
    val stringTag = mkStringConstant("[object String]", scope)
    val numberTag = mkStringConstant("[object Number]", scope)
    val booleanTag = mkStringConstant("[object Boolean]", scope)
    val mapTag = mkStringConstant("[object Map]", scope)
    val nullTag = mkStringConstant("[object Null]", scope)
    val undefinedTag = mkStringConstant("[object Undefined]", scope)
    val mapType = scene.sdkClasses
        .filter { it.name == "Map" }
        .maxByOrNull { it.methods.size }
        ?.type

    fun tagReference(ref: UExpr<UAddressSort>): UExpr<UAddressSort> {
        val isUndefined = mkHeapRefEq(ref, mkUndefinedValue())
        val isNull = mkHeapRefEq(ref, mkTsNullValue())
        val isArray = scope.calcOnState { memory.types.evalIsSubtype(ref, exactUnknownArrayType) }
        val isString = scope.calcOnState { memory.types.evalTypeEquals(ref, EtsStringType) }
        val isMap = mapType?.let { type ->
            scope.calcOnState { memory.types.evalIsSubtype(ref, type) }
        } ?: falseExpr
        return mkIte(
            isUndefined,
            undefinedTag,
            mkIte(
                isNull,
                nullTag,
                mkIte(isArray, arrayTag, mkIte(isString, stringTag, mkIte(isMap, mapTag, objectTag))),
            ),
        )
    }

    when {
        subject.isFakeObject() -> {
            val type = subject.getFakeType(scope)
            mkIte(
                type.boolTypeExpr,
                booleanTag,
                mkIte(
                    type.fpTypeExpr,
                    numberTag,
                    tagReference(subject.extractRef(scope)),
                ),
            )
        }

        subject.sort == boolSort -> booleanTag
        subject.sort == fp64Sort -> numberTag
        subject.sort == addressSort -> tagReference(subject.asExpr(addressSort))
        else -> objectTag
    }
}

private fun TsExprResolver.findBuiltinFunctionName(instance: EtsEntity): String? {
    val local = instance as? EtsLocal ?: return null
    val field = uniqueFieldDefinition(local) ?: return null
    return field.field.name.takeIf {
        it in setOf("toString", "hasOwnProperty") && isObjectPrototypeValue(field.instance)
    }
}

private fun TsExprResolver.isObjectPrototypeValue(instance: EtsLocal): Boolean {
    val field = uniqueFieldDefinition(instance) ?: return false
    return field.field.name == "prototype" && field.instance.name == "Object"
}

private fun TsExprResolver.uniqueFieldDefinition(local: EtsLocal): EtsInstanceFieldRef? {
    val method = scope.calcOnState { lastEnteredMethod }
    val definitions = method.cfg.stmts
        .filterIsInstance<EtsAssignStmt>()
        .filter { it.lhv == local }
    if (definitions.size != 1) return null
    return definitions.single().rhv as? EtsInstanceFieldRef
}

private fun EtsInstanceCallExpr.isMapReceiver(): Boolean =
    callee.enclosingClass.name == "Map" || instance.type.isMapType()

private fun EtsType.isMapType(): Boolean =
    toString().startsWith("Map<") || toString() == "Map"

private fun TsExprResolver.rejectBuiltin(
    expr: EtsInstanceCallExpr,
    detail: String,
    reason: SymbolicSemanticReason = SymbolicSemanticReason.BUILTIN_OUTSIDE_EXACT_SUBSET,
): TsExprApproximationResult.ResolveFailure {
    recordSymbolicSemanticFallback(reason, "${expr.callee}:$detail")
    scope.assert(ctx.falseExpr)
    return TsExprApproximationResult.ResolveFailure
}
