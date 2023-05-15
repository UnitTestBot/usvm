package org.usvm.memory

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.Field
import org.usvm.Type
import org.usvm.UAddressSort
import org.usvm.UContext
import org.usvm.sampleUValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeapMemsetTest {
    private lateinit var ctx: UContext
    private lateinit var heap: URegionHeap<Field, Type>
    private lateinit var arrayType: Type
    private lateinit var arrayValueSort: UAddressSort

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
        heap = URegionHeap(ctx)
        arrayType = mockk<Type>()
        arrayValueSort = ctx.addressSort
    }

    @Test
    fun testReadFromMemset() = with(ctx) {
        val concreteAddresses = (17..30).toList()
        val values = concreteAddresses.map { ctx.mkConcreteHeapRef(it) }

        val ref = heap.allocateArray(ctx.mkSizeExpr(concreteAddresses.size))
        val initiallyStoredValues = concreteAddresses.indices.map { idx ->
            heap.readArrayIndex(ref, mkSizeExpr(idx), arrayType, arrayValueSort)
        }

        heap.memset(ref, arrayType, arrayValueSort, values.asSequence())

        val storedValues = concreteAddresses.indices.map { idx ->
            heap.readArrayIndex(ref, mkSizeExpr(idx), arrayType, arrayValueSort)
        }

        assertTrue { initiallyStoredValues.all { it == arrayValueSort.sampleUValue() } }
        assertEquals(values, storedValues)
    }

    @Test
    fun testAllocWithInitialsRead() = with(ctx) {
        val concreteAddresses = (17..30).toList()
        val values = concreteAddresses.map { ctx.mkConcreteHeapRef(it) }

        val ref = heap.allocateArrayInitialized(arrayType, arrayValueSort, values.asSequence())

        val storedValues = concreteAddresses.indices.map { idx ->
            heap.readArrayIndex(ref, mkSizeExpr(idx), arrayType, arrayValueSort)
        }

        assertEquals(values, storedValues)
    }

}
