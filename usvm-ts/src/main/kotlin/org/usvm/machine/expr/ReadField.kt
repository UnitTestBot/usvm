package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsStaticFieldRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
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
    val instance = resolve(instanceLocal) ?: return null
    check(instance.sort == addressSort) {
        "Expected address sort for instance, got: ${instance.sort}"
    }
    val instanceRef = instance.asExpr(addressSort)

    // TODO: consider moving this to 'readField'
    // Check for undefined or null property access.
    checkUndefinedOrNullPropertyRead(scope, instanceRef, value.field.name) ?: return null

    // Handle reading "length" property for arrays.
    if (value.field.name == "length" && instanceLocal.type is EtsArrayType) {
        return readLengthArray(scope, instanceLocal, instanceRef)
    }

    // Handle reading "length" property for fake objects.
    // TODO: handle "length" property for arrays inside fake objects
    if (value.field.name == "length" && instanceRef.isFakeObject()) {
        return readLengthFake(scope, instanceLocal, instanceRef)
    }

    // Read the field.
    return readField(scope, instanceLocal, instanceRef, value.field, hierarchy)
}

internal fun TsContext.readField(
    scope: TsStepScope,
    instanceLocal: EtsLocal?,
    instance: UHeapRef,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*> {
    // Unwrap to get non-fake reference.
    val unwrappedInstance = instance.unwrapRef(scope)

    val sort = when (val etsField = resolveEtsField(instanceLocal, field, hierarchy)) {
        is TsResolutionResult.Empty -> {
            if (field.name !in listOf("i", "LogLevel")) {
                logger.warn { "Field $field not found, creating fake field" }
            }
            // If we didn't find any real fields, let's create a fake one.
            // It is possible due to mistakes in the IR or if the field was added explicitly
            // in the code.
            // Probably, the right behaviour here is to fork the state.
            unwrappedInstance.createFakeField(scope, field.name)
            addressSort
        }

        is TsResolutionResult.Unique -> typeToSort(etsField.property.type)

        is TsResolutionResult.Ambiguous -> unresolvedSort
    }

    scope.doWithState {
        // If we accessed some field, we make an assumption that
        // this field should present in the object.
        // That's not true in the common case for TS, but that's the decision we made.
        val auxiliaryType = EtsAuxiliaryType(properties = setOf(field.name))
        // assert is required to update models
        scope.assert(memory.types.evalIsSubtype(unwrappedInstance, auxiliaryType))
    }

    // If the field type is known, we can read it directly.
    if (sort !is TsUnresolvedSort) {
        val lValue = mkFieldLValue(sort, unwrappedInstance, field)
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
            val fakeObj = mkFakeValue(bool, fp, ref)
            lValuesToAllocatedFakeObjects += refLValue to fakeObj
            memory.write(refLValue, fakeObj, guard = trueExpr)
            fakeObj
        }
    }
}

internal fun TsExprResolver.handleStaticFieldRef(
    value: EtsStaticFieldRef,
): UExpr<*>? = with(ctx) {
    return readStaticField(scope, value.field, hierarchy)
}

internal fun TsContext.readStaticField(
    scope: TsStepScope,
    field: EtsFieldSignature,
    hierarchy: EtsHierarchy,
): UExpr<*>? {
    // TODO: handle unresolved class, or multiple classes
    val clazz = scene.projectAndSdkClasses.singleOrNull {
        it.signature == field.enclosingClass
    } ?: return null

    // Initialize statics in `clazz` if necessary.
    ensureStaticsInitialized(scope, clazz) ?: return null

    // Get the static instance.
    val instance = scope.calcOnState { getStaticInstance(clazz) }

    // Read the field.
    return readField(scope, null, instance, field, hierarchy)
}
