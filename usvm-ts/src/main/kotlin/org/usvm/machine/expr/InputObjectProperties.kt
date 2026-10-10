package org.usvm.machine.expr

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.isFalse
import org.usvm.machine.TsContext
import org.usvm.machine.TsInputPropertyPresence
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.EtsObjectType
import org.usvm.machine.types.TsUnresolvedValue
import org.usvm.machine.types.extractValue
import org.usvm.machine.types.iteUnresolvedValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.util.EtsHierarchy
import org.usvm.util.getAllMethods
import org.usvm.util.mkFieldLValue

private enum class PropertySlot {
    INITIAL_PRESENCE,
    BOOL_KIND,
    NUMBER_KIND,
    REF_KIND,
    BOOL_VALUE,
    NUMBER_VALUE,
    REF_VALUE,
}
private data class PropertyField(val name: String, val slot: PropertySlot, val written: Boolean)

private fun <S : USort> propertyLValue(sort: S, ref: UHeapRef, name: String, slot: PropertySlot, written: Boolean) =
    UFieldLValue(sort, ref, PropertyField(name, slot, written))

internal fun TsContext.initialPropertyPresenceLValue(ref: UHeapRef, name: String): UFieldLValue<*, UBoolSort> =
    propertyLValue(boolSort, ref, name, PropertySlot.INITIAL_PRESENCE, written = false)

/** Resolve only the receiver's declaration, never an unrelated same-name field in the scene. */
internal fun declaredInputField(local: EtsLocal?, name: String, hierarchy: EtsHierarchy): EtsField? {
    val type = local?.type
    if (type !is EtsClassType && type !is EtsUnclearRefType) return null

    val fields = hierarchy.classesForType(type).flatMap { receiver ->
        val owners = hierarchy.getAncestors(receiver).filter { owner ->
            owner.fields.any { !it.isStatic && it.name == name }
        }
        // A redeclaration shadows the ancestor's type and optional flag.
        val nearestOwners = owners.filter { owner ->
            owners.none { descendant -> owner != descendant && owner in hierarchy.getAncestors(descendant) }
        }
        nearestOwners.flatMap { owner -> owner.fields.filter { !it.isStatic && it.name == name } }
    }
    return fields.distinctBy { it.signature }.singleOrNull()
}

internal fun TsContext.trackInputProperty(
    scope: TsStepScope,
    instance: UHeapRef,
    local: EtsLocal?,
    name: String,
    hierarchy: EtsHierarchy,
): UBoolExpr? {
    val unsupported = inputPropertyUnsupportedReason(local, name, hierarchy)
    if (unsupported != null) throw UnsupportedOperationException(unsupported)

    // Reference-sort payloads also contain strings; only runtime objects can receive own fields.
    val objectReceiver = scope.calcOnState {
        memory.types.evalIsSubtype(instance, EtsObjectType)
    }
    scope.assert(objectReceiver) ?: return null

    val field = declaredInputField(local, name, hierarchy)
    val initial = scope.calcOnState { memory.read(initialPropertyPresenceLValue(instance, name)) }
    val assumedPresence = when (scope.calcOnState { inputPropertyPresence }) {
        TsInputPropertyPresence.DECLARED_FIELDS -> {
            if (field != null && (field as? EtsFieldImpl)?.isOptional != true) true else null
        }
        TsInputPropertyPresence.SYMBOLIC -> null
        TsInputPropertyPresence.ASSUME_PRESENT -> true
        TsInputPropertyPresence.ASSUME_ABSENT -> false
    }
    if (assumedPresence != null) {
        val presenceConstraint = if (assumedPresence) initial else mkNot(initial)
        scope.assert(presenceConstraint) ?: return null
    }

    scope.doWithState {
        val optional = (field as? EtsFieldImpl)?.isOptional == true
        trackedObjectProperties += TrackedObjectProperty(instance, name, field?.type, optional = optional)
    }
    return initial
}

private fun inputPropertyUnsupportedReason(local: EtsLocal?, name: String, hierarchy: EtsHierarchy): String? {
    if (name in OBJECT_PROTOTYPE_PROPERTIES) return "Input property '$name' requires unsupported prototype lookup"

    val type = local?.type
    if (type is EtsArrayType) return "Named input array properties require array presence semantics"
    if (type !is EtsClassType && type !is EtsUnclearRefType) return null

    val inheritedMethod = hierarchy.classesForType(type).flatMap { it.getAllMethods(hierarchy) }
        .any { !it.isStatic && it.name == name }
    return if (inheritedMethod) "Input property '$name' requires unsupported class prototype lookup" else null
}

internal fun TsContext.inputPropertyPresence(
    scope: TsStepScope,
    instance: UHeapRef,
    name: String,
    initial: UBoolExpr,
): UBoolExpr = scope.calcOnState {
    val written = memory.read(writtenPropertyLValue(instance, name))
    val deleted = memory.read(deletedFieldLValue(instance, name))
    val everPresent = mkOr(initial, written)
    mkAnd(everPresent, mkNot(deleted))
}

/** Input values use real field payloads; kind selectors and mutation payloads have separate synthetic regions. */
internal fun TsContext.readInputPropertyValue(
    memory: UReadOnlyMemory<EtsType>,
    instance: UHeapRef,
    name: String,
    written: Boolean,
): TsUnresolvedValue {
    fun <S : USort> payload(sort: S, slot: PropertySlot): UExpr<S> = if (written) {
        memory.read(propertyLValue(sort, instance, name, slot, written = true))
    } else {
        memory.read(mkFieldLValue(sort, instance, name))
    }
    fun kind(slot: PropertySlot) = memory.read(propertyLValue(boolSort, instance, name, slot, written))

    val type = EtsFakeType(
        boolTypeExpr = kind(PropertySlot.BOOL_KIND),
        fpTypeExpr = kind(PropertySlot.NUMBER_KIND),
        refTypeExpr = kind(PropertySlot.REF_KIND),
    )
    return TsUnresolvedValue(
        boolValue = payload(boolSort, PropertySlot.BOOL_VALUE),
        fpValue = payload(fp64Sort, PropertySlot.NUMBER_VALUE),
        refValue = payload(addressSort, PropertySlot.REF_VALUE),
        type = type,
    )
}

internal fun TsContext.writeInputPropertyValue(scope: TsStepScope, instance: UHeapRef, name: String, value: UExpr<*>) {
    scope.doWithState {
        val (bool, boolKind) = extractValue(value, boolSort, ::getIntermediateBoolLValue)
        val (number, numberKind) = extractValue(value, fp64Sort, ::getIntermediateFpLValue)
        val (ref, refKind) = extractValue(value, addressSort, ::getIntermediateRefLValue)

        fun <S : USort> write(slot: PropertySlot, sort: S, payload: UExpr<S>) {
            val lValue = propertyLValue(sort, instance, name, slot, written = true)
            memory.write(lValue, payload, guard = trueExpr)
        }

        write(PropertySlot.BOOL_KIND, boolSort, boolKind)
        write(PropertySlot.NUMBER_KIND, boolSort, numberKind)
        write(PropertySlot.REF_KIND, boolSort, refKind)
        bool?.let { write(PropertySlot.BOOL_VALUE, boolSort, it) }
        number?.let { write(PropertySlot.NUMBER_VALUE, fp64Sort, it) }
        ref?.let { write(PropertySlot.REF_VALUE, addressSort, it) }

        memory.write(writtenPropertyLValue(instance, name), trueExpr, guard = trueExpr)
        memory.write(deletedFieldLValue(instance, name), falseExpr, guard = trueExpr)
    }
}

/** Select payloads before materializing a wrapper, so symbolic aliases never turn wrappers into ordinary objects. */
internal fun TsContext.currentInputPropertyValue(
    memory: UReadOnlyMemory<EtsType>,
    instance: UHeapRef,
    name: String,
    initial: TsUnresolvedValue,
): TsUnresolvedValue {
    val written = memory.read(writtenPropertyLValue(instance, name))
    if (written.isFalse) return initial

    val value = readInputPropertyValue(memory, instance, name, written = true)
    return iteUnresolvedValue(written, value, initial)
}
