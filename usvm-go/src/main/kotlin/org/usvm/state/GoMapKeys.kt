package org.usvm.state

import org.jacodb.go.api.GoType
import org.jacodb.go.api.MapType
import org.usvm.GoContext
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.refSetContainsElement
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.memory.UReadOnlyMemory

/** Unselected representative branches cannot create extra equal-value input-map entries. */
internal fun GoState.mapKeyCanonicalityConstraint(
    map: UHeapRef,
    type: MapType,
    sourceMemory: UReadOnlyMemory<GoType> = memory,
): UBoolExpr = with(ctx) {
    var result = trueExpr
    for (entry in data.mapKeyRepresentatives[type.keyType].orEmpty()) {
        val canonical = mkHeapRefEq(entry.reference, entry.canonicalReference)
        val present = sourceMemory.refSetContainsElement(map, entry.reference, type)
        val valid = mkOr(mkNot(entry.guard), canonical, mkNot(present))
        result = mkAnd(result, valid)
    }
    result
}

/** Storage identity and the supplied Go value have separate responsibilities. */
internal data class GoResolvedMapKey(val key: UExpr<out USort>, val snapshot: UExpr<out USort>)

/** Separate region: actual per-map key payloads cannot collide with map values. */
internal data class GoMapKeyPayloadRegion(val mapType: MapType)

internal fun GoContext.mapKeyPayloadLValue(
    map: UHeapRef,
    key: UHeapRef,
    type: MapType,
): URefMapEntryLValue<GoMapKeyPayloadRegion, USort> =
    URefMapEntryLValue(typeToSort(type.keyType), map, key, GoMapKeyPayloadRegion(type))
