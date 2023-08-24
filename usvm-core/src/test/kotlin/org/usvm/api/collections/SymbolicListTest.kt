package org.usvm.api.collections

import io.ksmt.solver.KSolver
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.api.ListCollectionApi.mkSymbolicList
import org.usvm.api.ListCollectionApi.symbolicListAdd
import org.usvm.api.ListCollectionApi.symbolicListGet
import org.usvm.api.ListCollectionApi.symbolicListInsert
import org.usvm.api.ListCollectionApi.symbolicListRemove
import org.usvm.api.ListCollectionApi.symbolicListSet
import org.usvm.api.ListCollectionApi.symbolicListSize
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.Test

class SymbolicListTest : SymbolicCollectionTestBase() {

    private val listType = SingleTypeSystem.SingleType

    @Test
    fun testConcreteListValues() {
        val concreteList = state.mkSymbolicList(listType)
        testListValues(concreteList)
    }

    @Test
    fun testSymbolicListValues() {
        val symbolicList = ctx.mkRegisterReading(99, ctx.addressSort)
        testListValues(symbolicList)
    }

    private fun testListValues(listRef: UHeapRef) {
        val initialSize = state.symbolicListSize(listRef, listType)

        val listValues = (1..5).mapTo(mutableListOf()) { ctx.mkSizeExpr(it) }
        listValues.forEach {
            state.symbolicListAdd(listRef, listType, ctx.sizeSort, it)
        }

        checkValues(listRef, listValues, initialSize)

        val modifiedIdx = listValues.size / 2
        val modifiedValue = ctx.mkSizeExpr(42)
        listValues[modifiedIdx] = modifiedValue
        val modifiedListIdx = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(modifiedIdx))
        state.symbolicListSet(listRef, listType, ctx.sizeSort, modifiedListIdx, modifiedValue)

        checkValues(listRef, listValues, initialSize)

        val removeIdx = listValues.size / 2
        listValues.removeAt(removeIdx)
        val removeListIdx = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(removeIdx))
        state.symbolicListRemove(listRef, listType, ctx.sizeSort, removeListIdx)

        checkValues(listRef, listValues, initialSize)

        val insertIdx = listValues.size / 2
        val insertValue = ctx.mkSizeExpr(17)
        listValues.add(insertIdx, insertValue)
        val insertListIdx = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(removeIdx))
        state.symbolicListInsert(listRef, listType, ctx.sizeSort, insertListIdx, insertValue)

        checkValues(listRef, listValues, initialSize)
    }

    @Test
    fun testConcreteListBoundModification() {
        val concreteList = state.mkSymbolicList(listType)
        testListBoundModification(concreteList)
    }

    @Test
    fun testSymbolicListBoundModification() {
        val symbolicList = ctx.mkRegisterReading(99, ctx.addressSort)
        testListBoundModification(symbolicList)
    }

    private fun testListBoundModification(listRef: UHeapRef) {
        val initialSize = state.symbolicListSize(listRef, listType)

        val listValues = (1..5).mapTo(mutableListOf()) { ctx.mkSizeExpr(it) }
        listValues.forEach {
            state.symbolicListAdd(listRef, listType, ctx.sizeSort, it)
        }

        checkValues(listRef, listValues, initialSize)

        // remove first
        listValues.removeAt(0)
        state.symbolicListRemove(listRef, listType, ctx.sizeSort, initialSize)

        checkValues(listRef, listValues, initialSize)

        // insert first
        val insertHeadValue = ctx.mkSizeExpr(17)
        listValues.add(0, insertHeadValue)
        state.symbolicListInsert(listRef, listType, ctx.sizeSort, initialSize, insertHeadValue)

        checkValues(listRef, listValues, initialSize)

        // remove last
        listValues.removeAt(listValues.lastIndex)
        run {
            val listSize = state.symbolicListSize(listRef, listType)
            state.symbolicListRemove(listRef, listType, ctx.sizeSort, ctx.mkBvSubExpr(listSize, ctx.mkSizeExpr(1)))
        }

        checkValues(listRef, listValues, initialSize)

        // insert last
        val insertTailValue = ctx.mkSizeExpr(17)
        listValues.add(listValues.size, insertTailValue)
        run {
            val listSize = state.symbolicListSize(listRef, listType)
            state.symbolicListInsert(listRef, listType, ctx.sizeSort, listSize, insertTailValue)
        }

        checkValues(listRef, listValues, initialSize)
    }

    private fun checkValues(listRef: UHeapRef, values: List<USizeExpr>, initialSize: USizeExpr) {
        val listValues = values.indices.map { idx ->
            val listIndex = ctx.mkBvAddExpr(initialSize, ctx.mkSizeExpr(idx))
            state.symbolicListGet(listRef, listIndex, listType, ctx.sizeSort)
        }
        checkWithSolver {
            values.zip(listValues) { expectedValue, actualValue ->
                assertImpossible {
                    mkAnd(
                        inputListSizeAssumption(initialSize),
                        actualValue neq expectedValue
                    )
                }
            }
        }
    }

    @Test
    fun testConcreteListSize() {
        val concreteList = state.mkSymbolicList(listType)
        testListSize(concreteList) { actualSize, expectedSize ->
            assertImpossible { actualSize neq expectedSize }
        }
    }

    @Test
    fun testSymbolicListSize() {
        val symbolicList = ctx.mkRegisterReading(99, ctx.addressSort)
        val initialSize = state.symbolicListSize(symbolicList, listType)

        testListSize(symbolicList) { actualSize, expectedSize ->
            assertImpossible {
                mkAnd(
                    inputListSizeAssumption(initialSize),
                    mkBvSignedLessExpr(actualSize, expectedSize)
                )
            }
        }
    }

    private fun testListSize(listRef: UHeapRef, checkSize: KSolver<*>.(USizeExpr, USizeExpr) -> Unit) {
        val numValues = 5
        repeat(numValues) {
            state.symbolicListAdd(listRef, listType, ctx.sizeSort, ctx.mkSizeExpr(it))
        }

        checkWithSolver {
            val actualSize = state.symbolicListSize(listRef, listType)
            checkSize(actualSize, ctx.mkSizeExpr(numValues))
        }

        state.symbolicListInsert(
            listRef = listRef,
            listType = listType,
            sort = ctx.sizeSort,
            index = ctx.mkSizeExpr(0),
            value = ctx.mkSizeExpr(17)
        )

        checkWithSolver {
            val actualSize = state.symbolicListSize(listRef, listType)
            checkSize(actualSize, ctx.mkSizeExpr(numValues + 1))
        }

        state.symbolicListRemove(
            listRef = listRef,
            listType = listType,
            sort = ctx.sizeSort,
            index = ctx.mkSizeExpr(numValues / 2)
        )

        checkWithSolver {
            val actualSize = state.symbolicListSize(listRef, listType)
            checkSize(actualSize, ctx.mkSizeExpr(numValues))
        }
    }

    // Constraint size to avoid overflow
    private fun UContext.inputListSizeAssumption(size: USizeExpr) =
        mkAnd(
            mkBvSignedGreaterOrEqualExpr(size, mkSizeExpr(0)),
            mkBvSignedLessOrEqualExpr(size, mkSizeExpr(1000)),
        )
}
