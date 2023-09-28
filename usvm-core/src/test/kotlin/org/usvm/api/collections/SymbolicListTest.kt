package org.usvm.api.collections

import io.ksmt.solver.KSolver
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.UState
import org.usvm.api.collection.ListCollectionApi.ensureListSizeCorrect
import org.usvm.api.collection.ListCollectionApi.mkSymbolicList
import org.usvm.api.collection.ListCollectionApi.symbolicListAdd
import org.usvm.api.collection.ListCollectionApi.symbolicListGet
import org.usvm.api.collection.ListCollectionApi.symbolicListInsert
import org.usvm.api.collection.ListCollectionApi.symbolicListRemove
import org.usvm.api.collection.ListCollectionApi.symbolicListSet
import org.usvm.api.collection.ListCollectionApi.symbolicListSize
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.sizeSort
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.Test
import kotlin.test.assertNotNull

class SymbolicListTest : SymbolicCollectionTestBase() {

    private val listType = SingleTypeSystem.SingleType

    @Test
    fun testConcreteListValues() {
        val concreteList = scope.calcOnState { mkSymbolicList(listType) }
        testListValues(concreteList)
    }

    @Test
    fun testSymbolicListValues() = with(ctx) {
        val symbolicList = mkRegisterReading(99, addressSort)

        assertNotNull(scope.assert(mkHeapRefEq(symbolicList, nullRef).not()))
        assertNotNull(scope.ensureListSizeCorrect(symbolicList, listType))

        testListValues(symbolicList)
    }

    private fun testListValues(listRef: UHeapRef) = scope.doWithState {
        val initialSize = symbolicListSize(listRef, listType)

        val listValues = (1..5).mapTo(mutableListOf()) { ctx.mkSizeExpr(it) }
        listValues.forEach {
            symbolicListAdd(listRef, listType, ctx.sizeSort, it)
        }

        checkValues(listRef, listValues, initialSize)

        val modifiedIdx = listValues.size / 2
        val modifiedValue = ctx.mkSizeExpr(42)
        listValues[modifiedIdx] = modifiedValue
        val modifiedListIdx = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(modifiedIdx))
        symbolicListSet(listRef, listType, ctx.sizeSort, modifiedListIdx, modifiedValue)

        checkValues(listRef, listValues, initialSize)

        val removeIdx = listValues.size / 2
        listValues.removeAt(removeIdx)
        val removeListIdx = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(removeIdx))
        symbolicListRemove(listRef, listType, ctx.sizeSort, removeListIdx)

        checkValues(listRef, listValues, initialSize)

        val insertIdx = listValues.size / 2
        val insertValue = ctx.mkSizeExpr(17)
        listValues.add(insertIdx, insertValue)
        val insertListIdx = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(removeIdx))
        symbolicListInsert(listRef, listType, ctx.sizeSort, insertListIdx, insertValue)

        checkValues(listRef, listValues, initialSize)
    }

    @Test
    fun testConcreteListBoundModification() {
        val concreteList = scope.calcOnState { mkSymbolicList(listType) }
        testListBoundModification(concreteList)
    }

    @Test
    fun testSymbolicListBoundModification() = with(ctx) {
        val symbolicList = mkRegisterReading(99, addressSort)

        assertNotNull(scope.assert(mkHeapRefEq(symbolicList, nullRef).not()))
        assertNotNull(scope.ensureListSizeCorrect(symbolicList, listType))

        testListBoundModification(symbolicList)
    }

    private fun testListBoundModification(listRef: UHeapRef) = scope.doWithState {
        val initialSize = symbolicListSize(listRef, listType)

        val listValues = (1..5).mapTo(mutableListOf()) { ctx.mkSizeExpr(it) }
        listValues.forEach {
            symbolicListAdd(listRef, listType, ctx.sizeSort, it)
        }

        checkValues(listRef, listValues, initialSize)

        // remove first
        listValues.removeAt(0)
        symbolicListRemove(listRef, listType, ctx.sizeSort, initialSize)

        checkValues(listRef, listValues, initialSize)

        // insert first
        val insertHeadValue = ctx.mkSizeExpr(17)
        listValues.add(0, insertHeadValue)
        symbolicListInsert(listRef, listType, ctx.sizeSort, initialSize, insertHeadValue)

        checkValues(listRef, listValues, initialSize)

        // remove last
        listValues.removeAt(listValues.lastIndex)
        run {
            val listSize = symbolicListSize(listRef, listType)
            symbolicListRemove(listRef, listType, ctx.sizeSort, ctx.mkBvSubExpr(listSize, ctx.mkSizeExpr(1)))
        }

        checkValues(listRef, listValues, initialSize)

        // insert last
        val insertTailValue = ctx.mkSizeExpr(17)
        listValues.add(listValues.size, insertTailValue)
        run {
            val listSize = symbolicListSize(listRef, listType)
            symbolicListInsert(listRef, listType, ctx.sizeSort, listSize, insertTailValue)
        }

        checkValues(listRef, listValues, initialSize)
    }

    private fun UState<SingleTypeSystem.SingleType, *, *, UContext<USizeSort>, *, *>.checkValues(
        listRef: UHeapRef,
        values: List<UExpr<USizeSort>>,
        initialSize: UExpr<USizeSort>
    ) {
        val listValues = values.indices.map { idx ->
            val listIndex = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(idx))
            symbolicListGet(listRef, listIndex, listType, ctx.sizeSort)
        }
        checkWithSolver {
            values.zip(listValues) { expectedValue, actualValue ->
                assertImpossible {
                    with(ctx) {
                        mkAnd(
                            inputListSizeAssumption(initialSize),
                            actualValue neq expectedValue
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testConcreteListSize() {
        val concreteList = scope.calcOnState { mkSymbolicList(listType) }
        testListSize(concreteList) { actualSize, expectedSize ->
            assertImpossible { actualSize neq expectedSize }
        }
    }

    @Test
    fun testSymbolicListSize() = with(ctx) {
        val symbolicList = mkRegisterReading(99, ctx.addressSort)

        assertNotNull(scope.assert(mkHeapRefEq(symbolicList, nullRef).not()))
        assertNotNull(scope.ensureListSizeCorrect(symbolicList, listType))

        val initialSize = scope.calcOnState { symbolicListSize(symbolicList, listType) }

        testListSize(symbolicList) { actualSize, expectedSize ->
            assertImpossible {
                mkAnd(
                    inputListSizeAssumption(initialSize),
                    mkBvSignedLessExpr(actualSize, expectedSize)
                )
            }
        }
    }

    private fun testListSize(
        listRef: UHeapRef,
        checkSize: KSolver<*>.(UExpr<USizeSort>, UExpr<USizeSort>) -> Unit
    ) = scope.doWithState {
        val numValues = 5
        repeat(numValues) {
            symbolicListAdd(listRef, listType, ctx.sizeSort, ctx.mkSizeExpr(it))
        }

        checkWithSolver {
            val actualSize = symbolicListSize(listRef, listType)
            checkSize(actualSize, ctx.mkSizeExpr(numValues))
        }

        symbolicListInsert(
            listRef = listRef,
            listType = listType,
            sort = ctx.sizeSort,
            index = ctx.mkSizeExpr(0),
            value = ctx.mkSizeExpr(17)
        )

        checkWithSolver {
            val actualSize = symbolicListSize(listRef, listType)
            checkSize(actualSize, ctx.mkSizeExpr(numValues + 1))
        }

        symbolicListRemove(
            listRef = listRef,
            listType = listType,
            sort = ctx.sizeSort,
            index = ctx.mkSizeExpr(numValues / 2)
        )

        checkWithSolver {
            val actualSize = symbolicListSize(listRef, listType)
            checkSize(actualSize, ctx.mkSizeExpr(numValues))
        }
    }

    // Constraint size to avoid overflow
    private fun UContext<USizeSort>.inputListSizeAssumption(size: UExpr<USizeSort>) =
        mkAnd(
            mkSizeGeExpr(size, mkSizeExpr(0)),
            mkSizeLeExpr(size, mkSizeExpr(1000)),
        )
}
