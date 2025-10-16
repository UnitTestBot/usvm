package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsStaticFieldRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.ensureStaticsInitialized
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.mkFakeValue
import org.usvm.util.EtsHierarchy
import org.usvm.util.TsResolutionResult
import org.usvm.util.createFakeField
import org.usvm.util.mkFieldLValue
import org.usvm.util.resolveEtsField

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleInstanceFieldRef(
    value: EtsInstanceFieldRef,
): UExpr<*>? = with(ctx) {
    val instanceLocal = value.instance

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

    // TODO: consider moving this to 'readField'
    // Check for undefined or null property access.
    checkUndefinedOrNullPropertyRead(scope, instance, propertyName = value.field.name) ?: return null

    // Handle reading "length" property.
    if (value.field.name == "length") {
        return readLengthProperty(scope, instanceLocal, instance, options.maxArraySize)
    }

    // Read the field.
    return readField(scope, instanceLocal, instance, value.field, hierarchy)
}

fun TsContext.readField(
    scope: TsStepScope,
    instanceLocal: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    checkNotFake(instance)

    val sort = when (val etsField = resolveEtsField(instanceLocal, field, hierarchy)) {
        is TsResolutionResult.Empty -> {
            if (field.name !in listOf("i", "LogLevel")) {
                logger.warn { "Field $field not found, creating fake field" }
            }
            // If we didn't find any real fields, let's create a fake one.
            // It is possible due to mistakes in the IR or if the field was added explicitly
            // in the code.
            // Probably, the right behaviour here is to fork the state.
            instance.createFakeField(scope, field.name)
            addressSort
        }

        is TsResolutionResult.Unique -> {
            val cls = etsField.property.declaringClass
            if (cls != null && cls.category == EtsClassCategory.ENUM) {
                unresolvedSort
            } else {
                typeToSort(etsField.property.type)
            }
        }

        is TsResolutionResult.Ambiguous -> unresolvedSort
    }

    scope.calcOnState {
        // If we accessed some field, we make an assumption that
        // this field should present in the object.
        // That's not true in the common case for TS, but that's the decision we made.
        val auxiliaryType = EtsAuxiliaryType(properties = setOf(field.name))
        // assert is required to update models
        scope.assert(memory.types.evalIsSubtype(instance, auxiliaryType))
    } ?: return null

    // If the field type is known, we can read it directly.
    if (sort !is TsUnresolvedSort) {
        val lValue = mkFieldLValue(sort, instance, field)
        return scope.calcOnState { memory.read(lValue) }
    }

    // If the field type is unknown, we create a fake object.
    return scope.calcOnState {
        val boolLValue = mkFieldLValue(boolSort, instance, field)
        val fpLValue = mkFieldLValue(fp64Sort, instance, field)
        val refLValue = mkFieldLValue(addressSort, instance, field)

        val bool = memory.read(boolLValue)
        val fp = memory.read(fpLValue)
        val ref = memory.read(refLValue)

        // If a fake object is already created and assigned to the field,
        // there is no need to recreate another one.
        if (ref.isFakeObject()) {
            ref
        } else {
            val fakeObj = mkFakeValue(scope, bool, fp, ref)
            lValuesToAllocatedFakeObjects += refLValue to fakeObj
            memory.write(refLValue, fakeObj, guard = trueExpr)
            fakeObj
        }
    }
}

internal fun TsExprResolver.handleStaticFieldRef(
    value: EtsStaticFieldRef,
): UExpr<*>? = with(ctx) {
    return readStaticField(scope, value.field, hierarchy, options)
}

fun TsContext.readStaticField(
    scope: TsStepScope,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
    options: TsOptions,
): UExpr<*>? {
    // Resolve the static field using the existing resolver
    // Note: instance is null for static fields
    when (val result = resolveEtsField(instance = null, field = field, hierarchy = hierarchy)) {
        is TsResolutionResult.Unique -> {
            val resolvedField = result.property
            val clazz = resolvedField.declaringClass

            if (clazz == null) {
                logger.warn { "Resolved field $field has no declaring class" }
                if (options.isTargetedModeEnabled) {
                    return scope.calcOnState { mkFakeValue(scope) }
                } else {
                    scope.assert(falseExpr)
                    return null
                }
            }

            // Initialize statics in `clazz` if necessary.
            ensureStaticsInitialized(scope, clazz) ?: return null

            // Get the static instance.
            val instance = scope.calcOnState { getStaticInstance(clazz) }

            // Read the field.
            return readField(scope, null, instance, field, hierarchy)
        }

        is TsResolutionResult.Ambiguous -> {
            logger.warn { "Ambiguous static field resolution for $field: ${result.properties.size} candidates found" }
            if (options.isTargetedModeEnabled) {
                // In targeted mode, we cannot under-approximate, so we return a fake value
                return scope.calcOnState { mkFakeValue(scope) }
            } else {
                // In normal mode, we kill the state since we cannot proceed
                scope.assert(falseExpr)
                return null
            }
        }

        is TsResolutionResult.Empty -> {
            logger.warn { "Static field resolution failed for $field: not found" }
            if (options.isTargetedModeEnabled) {
                // In targeted mode, we cannot under-approximate, so we return a fake value
                return scope.calcOnState { mkFakeValue(scope) }
            } else {
                // In normal mode, we kill the state since we cannot proceed
                scope.assert(falseExpr)
                return null
            }
        }
    }
}
