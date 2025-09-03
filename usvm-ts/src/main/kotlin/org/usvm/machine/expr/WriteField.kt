package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStaticFieldRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.util.EtsHierarchy
import org.usvm.util.TsResolutionResult
import org.usvm.util.mkFieldLValue
import org.usvm.util.resolveEtsField

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleAssignToInstanceField(
    lhv: EtsInstanceFieldRef,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    val instanceLocal = lhv.instance
    val field = lhv.field

    // Resolve the instance.
    val instance: UHeapRef = run {
        val resolved = resolve(instanceLocal) ?: return null
        if (resolved.isFakeObject()) {
            scope.assert(resolved.getFakeType(scope).refTypeExpr) ?: run {
                logger.warn { "UNSAT after ensuring fake object is ref-typed" }
                return null
            }
            resolved.extractRef(scope)
        } else {
            check(resolved.sort == addressSort) {
                "Expected address sort for instance, got: ${resolved.sort}"
            }
            resolved.asExpr(addressSort)
        }
    }

    // Check for undefined or null field access.
    checkUndefinedOrNullPropertyRead(scope, instance, field.name) ?: return null

    // Assign to the field.
    assignToInstanceField(scope, instanceLocal, instance, field, expr, hierarchy)
}

fun TsContext.assignToInstanceField(
    scope: TsStepScope,
    instanceLocal: EtsLocal,
    instance: UHeapRef,
    field: EtsFieldSignature,
    expr: UExpr<*>,
    hierarchy: EtsHierarchy,
) {
    // Unwrap to get non-fake reference.
    val unwrappedInstance = instance.unwrapRef(scope)

    val etsField = resolveEtsField(instanceLocal, field, hierarchy)
    // If we access some field, we expect that the object must have this field.
    // It is not always true for TS, but we decided to process it so.
    val supertype = EtsAuxiliaryType(properties = setOf(field.name))
    // assert is required to update models
    scope.doWithState {
        scope.assert(memory.types.evalIsSubtype(unwrappedInstance, supertype))
    }

    // Determine the field sort.
    val sort = when (etsField) {
        is TsResolutionResult.Empty -> unresolvedSort
        is TsResolutionResult.Unique -> typeToSort(etsField.property.type)
        is TsResolutionResult.Ambiguous -> unresolvedSort
    }

    // If the field type is unknown, we create a fake object for the expr and assign it.
    // Otherwise, assign expr directly.
    scope.doWithState {
        if (sort is TsUnresolvedSort) {
            val fakeObject = expr.toFakeObject(scope)
            val lValue = mkFieldLValue(addressSort, unwrappedInstance, field)
            lValuesToAllocatedFakeObjects += lValue to fakeObject
            memory.write(lValue, fakeObject, guard = trueExpr)
        } else {
            val lValue = mkFieldLValue(sort, unwrappedInstance, field)
            if (lValue.sort != expr.sort) {
                if (expr.isFakeObject()) {
                    val lhvType = instanceLocal.type
                    val value = when (lhvType) {
                        is EtsBooleanType -> {
                            pathConstraints += expr.getFakeType(scope).boolTypeExpr
                            expr.extractBool(scope)
                        }

                        is EtsNumberType -> {
                            pathConstraints += expr.getFakeType(scope).fpTypeExpr
                            expr.extractFp(scope)
                        }

                        else -> {
                            pathConstraints += expr.getFakeType(scope).refTypeExpr
                            expr.extractRef(scope)
                        }
                    }
                    memory.write(lValue, value.asExpr(lValue.sort), guard = trueExpr)
                } else {
                    TODO("Support enums fields")
                }
            } else {
                memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
            }
        }
    }
}

internal fun TsExprResolver.handleAssignToStaticField(
    lhv: EtsStaticFieldRef,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    assignToStaticField(scope, lhv.field, expr)
}

fun TsContext.assignToStaticField(
    scope: TsStepScope,
    field: EtsFieldSignature,
    expr: UExpr<*>,
): Unit? {
    val clazz = scene.projectAndSdkClasses.singleOrNull {
        it.signature == field.enclosingClass
    } ?: return null

    val instance = scope.calcOnState { getStaticInstance(clazz) }

    // TODO: initialize the static field first
    //  Note: Since we are assigning to a static field, we can omit its initialization,
    //        if it does not have any side effects.

    val sort = run {
        val fields = clazz.fields.filter { it.name == field.name }
        if (fields.size == 1) {
            val field = fields.single()
            val sort = typeToSort(field.type)
            return@run sort
        }
        unresolvedSort
    }
    return if (sort == unresolvedSort) {
        val lValue = mkFieldLValue(addressSort, instance, field.name)
        val fakeObject = expr.toFakeObject(scope)
        scope.doWithState {
            lValuesToAllocatedFakeObjects += lValue to fakeObject
            memory.write(lValue, fakeObject, guard = trueExpr)
        }
    } else {
        val lValue = mkFieldLValue(sort, instance, field.name)
        scope.doWithState {
            memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
        }
    }
}
