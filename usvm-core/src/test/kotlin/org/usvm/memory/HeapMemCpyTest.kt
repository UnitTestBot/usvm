package org.usvm.memory

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.*
import org.usvm.api.allocateArray
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.api.writeArrayIndex
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UTypeConstraints
import kotlin.test.Test
import kotlin.test.assertEquals

class HeapMemCpyTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var ownership: MutabilityOwnership
    private lateinit var heap: UMemory<Type, Any>
    private lateinit var arrayType: Type
    private lateinit var arrayValueSort: USizeSort

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        ownership = MutabilityOwnership()
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        val eqConstraints = UEqualityConstraints(ctx, ownership)
        val typeConstraints = UTypeConstraints(ownership, components.mkTypeSystem(ctx), eqConstraints)
        heap = UMemory(ctx, ownership, typeConstraints)
        arrayType = mockk<Type>()
        arrayValueSort = ctx.sizeSort
    }

    @Test
    fun testMemCopyRemoveIndex() = with(ctx) {
        val (array, ref) = initializeArray()

        val srcFrom = 4
        val dstFrom = 3
        val srcTo = array.size
        val dstTo = dstFrom + (srcTo - srcFrom)
        array.copyInto(
            destination = array,
            destinationOffset = dstFrom,
            startIndex = srcFrom,
            endIndex = srcTo
        )

        heap.memcpy(
            srcRef = ref,
            dstRef = ref,
            type = arrayType,
            elementSort = arrayValueSort,
            fromSrcIdx = ctx.mkSizeExpr(srcFrom),
            fromDstIdx = ctx.mkSizeExpr(dstFrom),
            toDstIdx = ctx.mkSizeExpr(dstTo - 1),
            guard = ctx.trueExpr
        )

        checkArrayEquals(ref, array)
    }

    @Test
    fun testMemCopyInsertIndex() = with(ctx) {
        val (array, ref) = initializeArray()

        val srcFrom = 3
        val dstFrom = 4
        val srcTo = array.size - 1
        val dstTo = dstFrom + (srcTo - srcFrom)
        array.copyInto(
            destination = array,
            destinationOffset = dstFrom,
            startIndex = srcFrom,
            endIndex = srcTo
        )

        heap.memcpy(
            srcRef = ref,
            dstRef = ref,
            type = arrayType,
            elementSort = arrayValueSort,
            fromSrcIdx = ctx.mkSizeExpr(srcFrom),
            fromDstIdx = ctx.mkSizeExpr(dstFrom),
            toDstIdx = ctx.mkSizeExpr(dstTo - 1),
            guard = ctx.trueExpr
        )

        checkArrayEquals(ref, array)
    }

    private fun initializeArray(): Pair<IntArray, UConcreteHeapRef> {
        val array = IntArray(10) { it + 1 }
        val ref = heap.allocateArray(arrayType, ctx.sizeSort, ctx.mkSizeExpr(array.size))

        array.indices.forEach { idx ->
            heap.writeArrayIndex(
                ref = ref,
                index = ctx.mkSizeExpr(idx),
                type = arrayType,
                sort = arrayValueSort,
                value = ctx.mkSizeExpr(array[idx]),
                guard = ctx.trueExpr
            )
        }

        checkArrayEquals(ref, array)

        return array to ref
    }

    private fun checkArrayEquals(ref: UHeapRef, expected: IntArray) {
        val storedValues = expected.indices.map { idx ->
            heap.readArrayIndex(
                ref = ref,
                index = ctx.mkSizeExpr(idx),
                arrayType = arrayType,
                sort = arrayValueSort
            )
        }

        assertEquals(expected.map { ctx.mkSizeExpr(it) }, storedValues)
    }
}
