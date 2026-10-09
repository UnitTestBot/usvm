package org.usvm.api.collections

import io.ksmt.utils.asExpr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.UBv32Sort
import org.usvm.api.collection.ObjectMapCollectionApi.mkSymbolicObjectMap
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapContains
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapCopyIntoEmpty
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapGet
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapPut
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapRemove
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.mkSizeExpr
import org.usvm.sizeSort
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.assertEquals

class PrimitiveMapTest : SymbolicCollectionTestBase() {
    private val mapType = SingleTypeSystem.SingleType

    @Test
    fun overwriteAndRepeatedRemovalPreserveSize() = scope.doWithState {
        val map = mkSymbolicObjectMap(mapType)
        val key = ctx.mkBv(7)
        val keyInfo = USizeExprKeyInfo<UBv32Sort>()

        symbolicPrimitiveMapPut(map, key, ctx.mkBv(1), mapType, keyInfo)
        symbolicPrimitiveMapPut(map, key, ctx.mkBv(2), mapType, keyInfo)

        assertEquals(ctx.mkSizeExpr(1), symbolicObjectMapSize(map, mapType))
        assertEquals(ctx.mkBv(2), symbolicPrimitiveMapGet(map, key, mapType, ctx.bv32Sort, keyInfo))

        symbolicPrimitiveMapRemove(map, key, mapType, keyInfo)
        symbolicPrimitiveMapRemove(map, key, mapType, keyInfo)

        assertEquals(ctx.mkSizeExpr(0), symbolicObjectMapSize(map, mapType))
        assertEquals(ctx.falseExpr, symbolicPrimitiveMapContains(map, key, mapType, keyInfo))
    }

    @Test
    fun symbolicKeyEqualityControlsSize() = scope.doWithState {
        val map = mkSymbolicObjectMap(mapType)
        val first = ctx.mkRegisterReading(0, ctx.bv32Sort)
        val second = ctx.mkRegisterReading(1, ctx.bv32Sort)
        val keyInfo = USizeExprKeyInfo<UBv32Sort>()

        symbolicPrimitiveMapPut(map, first, ctx.mkBv(1), mapType, keyInfo)
        symbolicPrimitiveMapPut(map, second, ctx.mkBv(2), mapType, keyInfo)
        val size = symbolicObjectMapSize(map, mapType).asExpr(ctx.sizeSort)

        checkWithSolver {
            assertImpossible {
                mkAnd(mkEq(first, second), mkNot(mkEq(size, mkBv(1))))
            }
            assertImpossible {
                mkAnd(mkNot(mkEq(first, second)), mkNot(mkEq(size, mkBv(2))))
            }
        }
    }

    @Test
    fun copyPreservesEntriesAndSize() = scope.doWithState {
        val source = mkSymbolicObjectMap(mapType)
        val destination = mkSymbolicObjectMap(mapType)
        val key = ctx.mkBv(7)
        val keyInfo = USizeExprKeyInfo<UBv32Sort>()
        symbolicPrimitiveMapPut(source, key, ctx.mkBv(42), mapType, keyInfo)

        symbolicPrimitiveMapCopyIntoEmpty(destination, source, mapType, ctx.bv32Sort, ctx.bv32Sort, keyInfo)

        assertEquals(ctx.mkSizeExpr(1), symbolicObjectMapSize(destination, mapType))
        assertEquals(ctx.trueExpr, symbolicPrimitiveMapContains(destination, key, mapType, keyInfo))
        assertEquals(ctx.mkBv(42), symbolicPrimitiveMapGet(destination, key, mapType, ctx.bv32Sort, keyInfo))
    }

    @Test
    fun copyingIntoNonemptyMapIsRejected() = scope.doWithState {
        val source = mkSymbolicObjectMap(mapType)
        val destination = mkSymbolicObjectMap(mapType)
        val keyInfo = USizeExprKeyInfo<UBv32Sort>()
        symbolicPrimitiveMapPut(destination, ctx.mkBv(1), ctx.mkBv(42), mapType, keyInfo)

        assertThrows<IllegalArgumentException> {
            symbolicPrimitiveMapCopyIntoEmpty(destination, source, mapType, ctx.bv32Sort, ctx.bv32Sort, keyInfo)
        }
    }
}
