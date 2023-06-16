package org.usvm.intrinsics.collections

import io.ksmt.solver.KSolver
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.intrinsics.collections.SymbolicListIntrinsics.mkSymbolicList
import org.usvm.intrinsics.collections.SymbolicListIntrinsics.symbolicListAdd
import org.usvm.intrinsics.collections.SymbolicListIntrinsics.symbolicListInsert
import org.usvm.intrinsics.collections.SymbolicListIntrinsics.symbolicListRemove
import org.usvm.intrinsics.collections.SymbolicListIntrinsics.symbolicListSize
import kotlin.test.Test

class SymbolicListTest : SymbolicCollectionTestBase() {

    @Test
    fun testConcreteListSize() {
        val concreteList = state.mkSymbolicList(ctx.sizeSort)
        testListSize(concreteList) { actualSize, expectedSize ->
            assertImpossible { actualSize neq expectedSize }
        }
    }

    @Test
    fun testSymbolicListSize() {
        val symbolicList = ctx.mkRegisterReading(99, ctx.addressSort)
        val initialSize = state.symbolicListSize(symbolicList, ctx.sizeSort)

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
            state.symbolicListAdd(listRef, ctx.sizeSort, ctx.mkSizeExpr(it))
        }

        checkWithSolver {
            val actualSize = state.symbolicListSize(listRef, ctx.sizeSort)
            checkSize(actualSize, ctx.mkSizeExpr(numValues))
        }

        state.symbolicListInsert(
            listRef = listRef,
            elementSort = ctx.sizeSort,
            index = ctx.mkSizeExpr(0),
            value = ctx.mkSizeExpr(17)
        )

        checkWithSolver {
            val actualSize = state.symbolicListSize(listRef, ctx.sizeSort)
            checkSize(actualSize, ctx.mkSizeExpr(numValues + 1))
        }

        state.symbolicListRemove(
            listRef = listRef,
            elementSort = ctx.sizeSort,
            index = ctx.mkSizeExpr(numValues / 2)
        )

        checkWithSolver {
            val actualSize = state.symbolicListSize(listRef, ctx.sizeSort)
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
