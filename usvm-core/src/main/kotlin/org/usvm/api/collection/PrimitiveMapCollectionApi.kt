package org.usvm.api.collection

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.setContainsElement
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.primitive.mapMerge
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.USetRegionId
import org.usvm.collection.set.primitive.setEntries
import org.usvm.collection.set.primitive.setUnion
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.regions.Region
import org.usvm.sizeSort

object PrimitiveMapCollectionApi {
    fun <MapType, KeySort : USort, ValueSort: USort, Reg : Region<Reg>> UState<MapType, *, *, *, *, *>.symbolicPrimitiveMapGet(
        mapRef: UHeapRef,
        key: UExpr<KeySort>,
        mapType: MapType,
        valueSort: ValueSort,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    ): UExpr<ValueSort> = memory.read(UMapEntryLValue(key.sort, valueSort, mapRef, key, mapType, keyInfo))

    fun <MapType, KeySort : USort, Reg : Region<Reg>> UState<MapType, *, *, *, *, *>.symbolicPrimitiveMapContains(
        mapRef: UHeapRef,
        key: UExpr<KeySort>,
        mapType: MapType,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    ): UBoolExpr = memory.setContainsElement(mapRef, key, mapType, keyInfo)

    fun <MapType, KeySort : USort, Reg : Region<Reg>> UState<MapType, *, *, *, *, *>.symbolicPrimitiveMapAnyKey(
        mapRef: UHeapRef,
        mapType: MapType,
        keySort: KeySort,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    ): UExpr<KeySort> {
        val allKeys = memory.setEntries(mapRef, mapType, keySort, keyInfo)
        val symbolicKeys = mutableListOf<Pair<UExpr<KeySort>, UBoolExpr>>()
        for (entry in allKeys.entries) {
            val key = entry.setElement
            val contains = symbolicPrimitiveMapContains(mapRef, key, mapType, keyInfo)
            when {
                contains.isTrue -> return key
                contains.isFalse -> continue
                else -> symbolicKeys += key to contains
            }
        }

        val defaultKey = makeSymbolicPrimitive(keySort)
        return symbolicKeys.fold(defaultKey) { result, (key, contains) ->
            ctx.mkIte(contains, key, result)
        }
    }

    fun <MapType, USizeSort : USort, Ctx : UContext<USizeSort>, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> UState<MapType, *, *, Ctx, *, *>.symbolicPrimitiveMapPut(
        mapRef: UHeapRef,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        mapType: MapType,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    ) = with(ctx) {
        val mapContainsLValue = USetEntryLValue(key.sort, mapRef, key, mapType, keyInfo)
        val currentSize = symbolicObjectMapSize(mapRef, mapType)

        val keyIsInMap = memory.read(mapContainsLValue)
        val keyIsNew = mkNot(keyIsInMap)

        memory.write(UMapEntryLValue(key.sort, value.sort, mapRef, key, mapType, keyInfo), value, guard = trueExpr)
        memory.write(mapContainsLValue, rvalue = trueExpr, guard = trueExpr)

        val updatedSize = mkSizeAddExpr(currentSize, mkSizeExpr(1))
        memory.write(UMapLengthLValue(mapRef, mapType, sizeSort), updatedSize, keyIsNew)
    }

    fun <MapType, USizeSort : USort, Ctx : UContext<USizeSort>, KeySort : USort, Reg : Region<Reg>> UState<MapType, *, *, Ctx, *, *>.symbolicPrimitiveMapRemove(
        mapRef: UHeapRef,
        key: UExpr<KeySort>,
        mapType: MapType,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    ) = with(ctx) {
        val mapContainsLValue = USetEntryLValue(key.sort, mapRef, key, mapType, keyInfo)
        val currentSize = symbolicObjectMapSize(mapRef, mapType)

        val keyIsInMap = memory.read(mapContainsLValue)

        // todo: skip values update?
        memory.write(mapContainsLValue, rvalue = falseExpr, guard = trueExpr)

        val updatedSize = mkSizeSubExpr(currentSize, mkSizeExpr(1))
        memory.write(UMapLengthLValue(mapRef, mapType, sizeSort), updatedSize, keyIsInMap)
    }

    fun <MapType, USizeSort : USort, Ctx: UContext<USizeSort>, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> UState<MapType, *, *, Ctx, *, *>.symbolicPrimitiveMapMergeInto(
        dstRef: UHeapRef,
        srcRef: UHeapRef,
        mapType: MapType,
        keySort: KeySort,
        valueSort: ValueSort,
        keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    ) = with(ctx) {
        val srcMapSize = symbolicObjectMapSize(srcRef, mapType)
        val dstMapSize = symbolicObjectMapSize(dstRef, mapType)

        val containsSetId = USetRegionId(keySort, mapType, keyInfo)
        memory.mapMerge(srcRef, dstRef, mapType, keySort, valueSort, keyInfo, containsSetId.uncheckedCast(), guard = trueExpr)
        memory.setUnion(srcRef, dstRef, mapType, keySort, keyInfo, guard = trueExpr)

        // todo: precise map size approximation?
        // val sizeLowerBound = mkIte(mkBvSignedGreaterExpr(srcMapSize, dstMapSize), srcMapSize, dstMapSize)
        val sizeUpperBound = mkSizeAddExpr(srcMapSize, dstMapSize)
        memory.write(UMapLengthLValue(dstRef, mapType, sizeSort), sizeUpperBound, guard = trueExpr)
    }
}
