package org.usvm.api.collections

import io.ksmt.solver.KSolver
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.api.collection.ObjectMapCollectionApi.mkSymbolicObjectMap
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapContains
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapMergeInto
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapPut
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapRemove
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ObjectMapTest : SymbolicCollectionTestBase() {

    private val mapType = SingleTypeSystem.SingleType

    @Test
    fun testConcreteMapContains() {
        val concreteMap = scope.mkSymbolicObjectMap(mapType)
        testMapContains(concreteMap)
    }

    @Test
    fun testSymbolicMapContains() = with(ctx) {
        val symbolicMap = mkRegisterReading(99, ctx.addressSort)
        scope.assert(mkHeapRefEq(symbolicMap, nullRef).not())
        testMapContains(symbolicMap)
    }

    private fun testMapContains(mapRef: UHeapRef) {
        val concreteKeys = (1..5).map { ctx.mkConcreteHeapRef(it) }
        val symbolicKeys = (1..5).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val storedConcrete = concreteKeys.dropLast(1)
        val missedConcrete = concreteKeys.last()
        val storedSymbolic = symbolicKeys.dropLast(1)
        val missedSymbolic = symbolicKeys.last()

        fillMap(mapRef, storedConcrete + storedSymbolic, startValueIdx = 1)

        checkWithSolver {
            (storedConcrete + storedSymbolic).forEach { key ->
                assertImpossible {
                    val keyContains = scope.symbolicObjectMapContains(mapRef, key, mapType)
                    keyContains eq falseExpr
                }
            }

            assertImpossible {
                val keyContains = scope.symbolicObjectMapContains(mapRef, missedConcrete, mapType)
                keyContains eq trueExpr
            }

            assertPossible {
                val keyContains = scope.symbolicObjectMapContains(mapRef, missedSymbolic, mapType)
                keyContains eq falseExpr
            }
        }

        val removeConcrete = storedConcrete.first()
        val removeSymbolic = storedSymbolic.first()
        val removedKeys = listOf(removeConcrete, removeSymbolic)
        removedKeys.forEach { key ->
            scope.symbolicObjectMapRemove(mapRef, key, mapType)
        }

        checkWithSolver {
            removedKeys.forEach { key ->
                assertImpossible {
                    val keyContains = scope.symbolicObjectMapContains(mapRef, key, mapType)
                    keyContains eq trueExpr
                }
            }
        }
    }

    @Test
    fun testConcreteMapContainsComposition() {
        val concreteMap = scope.mkSymbolicObjectMap(mapType)
        testMapContainsComposition(concreteMap)
    }

    @Test
    fun testSymbolicMapContainsComposition() = with(ctx) {
        val symbolicMap = mkRegisterReading(99, addressSort)
        scope.assert(mkHeapRefEq(symbolicMap, nullRef).not())
        testMapContainsComposition(symbolicMap)
    }

    private fun testMapContainsComposition(mapRef: UHeapRef) = scope.doWithState {
        val concreteKeys = (1..5).map { ctx.mkConcreteHeapRef(it) }
        val symbolicKeys = (1..5).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val otherSymbolicKey = ctx.mkRegisterReading(symbolicKeys.size + 1, ctx.addressSort)

        fillMap(mapRef, concreteKeys + symbolicKeys, startValueIdx = 1)

        val otherKeyContains = scope.symbolicObjectMapContains(mapRef, otherSymbolicKey, mapType)
        pathConstraints += otherKeyContains

        val result = uSolver.checkWithSoftConstraints(pathConstraints)
        assertIs<USatResult<UModelBase<SingleTypeSystem.SingleType>>>(result)

        assertEquals(ctx.trueExpr, result.model.eval(otherKeyContains))

        val removedKeys = setOf(concreteKeys.first(), symbolicKeys.first(), otherSymbolicKey)
        removedKeys.forEach { key ->
            scope.symbolicObjectMapRemove(mapRef, key, mapType)
        }

        val removedKeysValues = removedKeys.mapTo(hashSetOf()) { result.model.eval(it) }
        (concreteKeys + symbolicKeys + otherSymbolicKey).forEach { key ->
            val keyContains = scope.symbolicObjectMapContains(mapRef, key, mapType)
            val keyContainsValue = result.model.eval(keyContains)
            val keyValue = result.model.eval(key)

            val expectedResult = ctx.mkBool(keyValue !in removedKeysValues)
            assertEquals(expectedResult, keyContainsValue)
        }
    }

    @Test
    fun testConcreteMapSize() {
        val concreteMap = scope.mkSymbolicObjectMap(mapType)
        testMapSize(concreteMap) { size, lowerBound, upperBound ->
            assertPossible { size eq upperBound }
            assertPossible { size eq lowerBound }
            assertImpossible {
                mkBvSignedLessExpr(size, lowerBound) or mkBvSignedGreaterExpr(size, upperBound)
            }
        }
    }

    @Test
    fun testSymbolicMapSize() = with(ctx) {
        val symbolicMap = mkRegisterReading(99, addressSort)
        scope.assert(mkHeapRefEq(symbolicMap, nullRef).not())
        testMapSize(symbolicMap) { size, lowerBound, upperBound ->
            assertPossible { size eq lowerBound }
            assertPossible { size eq upperBound }
        }
    }

    private fun testMapSize(mapRef: UHeapRef, checkSizeBounds: KSolver<*>.(USizeExpr, USizeExpr, USizeExpr) -> Unit) {
        val concreteKeys = (1..5).map { ctx.mkConcreteHeapRef(it) }
        val symbolicKeys = (1..5).map { ctx.mkRegisterReading(it, ctx.addressSort) }

        fillMap(mapRef, concreteKeys + symbolicKeys, startValueIdx = 1)

        checkWithSolver {
            val sizeLowerBound = ctx.mkSizeExpr(concreteKeys.size + 1) // +1 for at least one symbolic key
            val sizeUpperBound = ctx.mkSizeExpr(concreteKeys.size + symbolicKeys.size)

            val actualSize = assertNotNull(scope.symbolicObjectMapSize(mapRef, mapType))

            checkSizeBounds(actualSize, sizeLowerBound, sizeUpperBound)
        }

        val removedKeys = setOf(concreteKeys.first(), symbolicKeys.first())
        removedKeys.forEach { key ->
            scope.symbolicObjectMapRemove(mapRef, key, mapType)
        }

        checkWithSolver {
            /**
             * Size lower bound before remove: concrete size + 1
             * where we add 1 for at least one symbolic key
             *
             * Size after remove is concrete -1 since we remove 1 concrete key and one symbolic.
             * */
            val minKeySize = concreteKeys.size - 1
            val sizeLowerBound = ctx.mkSizeExpr(minKeySize)
            val sizeUpperBound = ctx.mkSizeExpr(concreteKeys.size + symbolicKeys.size - removedKeys.size)

            val actualSize = assertNotNull(scope.symbolicObjectMapSize(mapRef, mapType))

            checkSizeBounds(actualSize, sizeLowerBound, sizeUpperBound)
        }
    }

    @Test
    fun testMapMergeSymbolicIntoConcrete() = with(ctx) {
        val concreteMap = scope.mkSymbolicObjectMap(mapType)
        val symbolicMap = mkRegisterReading(99, addressSort)
        scope.assert(mkHeapRefEq(symbolicMap, nullRef).not())

        testMapMerge(concreteMap, symbolicMap)
    }

    @Test
    fun testMapMergeConcreteIntoSymbolic() = with(ctx) {
        val concreteMap = scope.mkSymbolicObjectMap(mapType)
        val symbolicMap = mkRegisterReading(99, addressSort)
        scope.assert(mkHeapRefEq(symbolicMap, nullRef).not())

        testMapMerge(concreteMap, symbolicMap)
    }

    @Test
    fun testMapMergeConcreteIntoConcrete() {
        val concreteMap0 = scope.mkSymbolicObjectMap(mapType)
        val concreteMap1 = scope.mkSymbolicObjectMap(mapType)

        testMapMerge(concreteMap0, concreteMap1)
    }

    @Test
    fun testMapMergeSymbolicIntoSymbolic() = with(ctx) {
        val symbolicMap0 = ctx.mkRegisterReading(99, addressSort)
        val symbolicMap1 = ctx.mkRegisterReading(999, addressSort)
        scope.assert(mkHeapRefEq(symbolicMap0, nullRef).not())
        scope.assert(mkHeapRefEq(symbolicMap1, nullRef).not())


        testMapMerge(symbolicMap0, symbolicMap1)
    }

    private fun testMapMerge(mergeTarget: UHeapRef, otherMap: UHeapRef) {
        val overlapConcreteKeys = (1..3).map { ctx.mkConcreteHeapRef(it) }
        val nonOverlapConcreteKeys0 = (11..13).map { ctx.mkConcreteHeapRef(it) }
        val nonOverlapConcreteKeys1 = (21..23).map { ctx.mkConcreteHeapRef(it) }

        val overlapSymbolicKeys = (31..33).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val nonOverlapSymbolicKeys0 = (41..43).map { ctx.mkRegisterReading(it, ctx.addressSort) }
        val nonOverlapSymbolicKeys1 = (51..53).map { ctx.mkRegisterReading(it, ctx.addressSort) }

        val tgtMapKeys = listOf(
            overlapConcreteKeys,
            nonOverlapConcreteKeys0,
            overlapSymbolicKeys,
            nonOverlapSymbolicKeys0
        ).flatten()

        val otherMapKeys = listOf(
            overlapConcreteKeys,
            nonOverlapConcreteKeys1,
            overlapSymbolicKeys,
            nonOverlapSymbolicKeys1
        ).flatten()

        val removedKeys = setOf(
            nonOverlapConcreteKeys0.first(),
            nonOverlapConcreteKeys1.first()
        )

        val tgtValues = fillMap(mergeTarget, tgtMapKeys, 256)
        val otherValues = fillMap(otherMap, otherMapKeys, 65536)

        for (key in removedKeys) {
            scope.symbolicObjectMapRemove(mergeTarget, key, mapType)
            scope.symbolicObjectMapRemove(otherMap, key, mapType)
        }

        scope.symbolicObjectMapMergeInto(mergeTarget, otherMap, mapType, ctx.sizeSort)

        val mergedContains0 = tgtMapKeys.map { scope.symbolicObjectMapContains(mergeTarget, it, mapType) }
        val mergedContains1 = otherMapKeys.map { scope.symbolicObjectMapContains(mergeTarget, it, mapType) }

        val mergedValues0 = tgtMapKeys.map { scope.symbolicObjectMapGet(mergeTarget, it, mapType, ctx.sizeSort) }
        val mergedValues1 = otherMapKeys.map { scope.symbolicObjectMapGet(mergeTarget, it, mapType, ctx.sizeSort) }

        mergedContains0.forEach { checkNoConcreteHeapRefs(it) }
        mergedContains1.forEach { checkNoConcreteHeapRefs(it) }

        mergedValues0.forEach { checkNoConcreteHeapRefs(it) }
        mergedValues1.forEach { checkNoConcreteHeapRefs(it) }

        checkWithSolver {
            val mergedNonOverlapKeys = listOf(
                nonOverlapConcreteKeys0,
                nonOverlapConcreteKeys1,
                nonOverlapSymbolicKeys0,
                nonOverlapSymbolicKeys1
            ).flatten() - removedKeys

            for (key in mergedNonOverlapKeys) {
                val keyContains = scope.symbolicObjectMapContains(mergeTarget, key, mapType)
                assertPossible { keyContains eq trueExpr }

                val storedValue = tgtValues[key] ?: otherValues[key] ?: error("$key was not stored")
                val actualValue: USizeExpr = scope.symbolicObjectMapGet(mergeTarget, key, mapType, ctx.sizeSort)
                assertPossible { storedValue eq actualValue }
            }

            for (key in removedKeys) {
                val keyContains = scope.symbolicObjectMapContains(mergeTarget, key, mapType)
                assertPossible { keyContains eq falseExpr }
            }

            val overlapKeys = listOf(
                overlapConcreteKeys,
                overlapSymbolicKeys
            ).flatten()

            for (key in overlapKeys) {
                val keyContains = scope.symbolicObjectMapContains(mergeTarget, key, mapType)
                assertPossible { keyContains eq trueExpr }

                val storedV1 = tgtValues.getValue(key)
                val storedV2 = otherValues.getValue(key)
                val actualValue: USizeExpr = scope.symbolicObjectMapGet(mergeTarget, key, mapType, ctx.sizeSort)

                assertPossible {
                    (actualValue eq storedV1) or (actualValue eq storedV2)
                }
            }
        }
    }

    private fun fillMap(mapRef: UHeapRef, keys: List<UHeapRef>, startValueIdx: Int) = with(scope) {
        keys.mapIndexed { index, key ->
            val value = ctx.mkSizeExpr(index + startValueIdx)
            symbolicObjectMapPut(
                mapRef,
                key,
                value,
                mapType,
                ctx.sizeSort
            )
            key to value
        }.toMap()
    }
}
