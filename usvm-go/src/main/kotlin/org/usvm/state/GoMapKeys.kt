package org.usvm.state

import org.jacodb.go.api.GoType
import org.jacodb.go.api.MapType
import org.usvm.GoContext
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.api.refSetContainsElement
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.sizeSort
import org.usvm.type.needsMapKeyRepresentative

/** Canonical identities and observed membership must agree with the declared map size. */
internal fun GoState.mapKeyCanonicalityConstraint(
    map: UHeapRef,
    type: MapType,
    sourceMemory: UReadOnlyMemory<GoType> = memory,
): UBoolExpr = with(ctx) {
    val usesRepresentatives = type.keyType.needsMapKeyRepresentative()
    var result: UBoolExpr = if (usesRepresentatives) {
        mkNot(sourceMemory.refSetContainsElement(map, nullRef, type))
    } else {
        trueExpr
    }
    var minimumSize: UExpr<USizeSort> = mkSizeExpr(0)
    val previousEntries = mutableListOf<GoMapKeyRepresentative>()
    for (entry in data.mapKeyRepresentatives[type.keyType].orEmpty()) {
        val canonical = mkHeapRefEq(entry.reference, entry.canonicalReference)
        val present = sourceMemory.refSetContainsElement(map, entry.reference, type)
        val valid = mkOr(mkNot(entry.guard), canonical, mkNot(present))
        result = mkAnd(result, valid)

        if (usesRepresentatives) {
            // Logical nil keys live in a nonnull proxy; inactive range proxies cannot add null entries.
            val nonnullProxy = mkNot(mkHeapRefEq(entry.reference, nullRef))
            result = mkAnd(result, mkOr(mkNot(present), nonnullProxy))
        }

        val previousMatches = previousEntries.map { previous ->
            mkAnd(previous.guard, mkHeapRefEq(previous.reference, entry.reference))
        }
        val alreadyCounted = mkOr(previousMatches)
        val contributes = mkAnd(entry.guard, present, mkNot(alreadyCounted))
        minimumSize = mkIte(contributes, mkSizeAddExpr(minimumSize, mkSizeExpr(1)), minimumSize)
        previousEntries += entry
    }
    val declaredSize = sourceMemory.read(UMapLengthLValue(map, type, sizeSort))
    mkAnd(result, mkSizeGeExpr(declaredSize, minimumSize))
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

/** Internal proxy payloads never share user tuple or boxed-value field indexes. */
internal data class GoMapKeySnapshotField(val keyType: GoType)

internal fun GoContext.mapKeySnapshotLValue(
    key: UHeapRef,
    type: GoType,
): UFieldLValue<GoMapKeySnapshotField, USort> =
    UFieldLValue(typeToSort(type), key, GoMapKeySnapshotField(type))
