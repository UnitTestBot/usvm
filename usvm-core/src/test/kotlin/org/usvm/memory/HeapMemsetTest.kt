package org.usvm.memory

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.Type
import org.usvm.UAddressSort
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeSort
import org.usvm.api.allocateArray
import org.usvm.api.allocateArrayInitialized
import org.usvm.api.memset
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.mkSizeExpr
import org.usvm.sampleUValue
import org.usvm.sizeSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeapMemsetTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var ownership: MutabilityOwnership
    private lateinit var heap: UMemory<Type, Any>
    private lateinit var arrayType: Type
    private lateinit var arrayValueSort: UAddressSort

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
        arrayValueSort = ctx.addressSort
    }

    @Test
    fun testReadFromMemset() = with(ctx) {
        val concreteAddresses = (17..30).toList()
        val values = concreteAddresses.map { mkConcreteHeapRef(it) }

        val ref = heap.allocateArray(arrayType, sizeSort, mkSizeExpr(concreteAddresses.size))
        val initiallyStoredValues = concreteAddresses.indices.map { idx ->
            heap.readArrayIndex(ref, mkSizeExpr(idx), arrayType, arrayValueSort)
        }

        heap.memset(ref, arrayType, arrayValueSort, sizeSort, values.asSequence())

        val storedValues = concreteAddresses.indices.map { idx ->
            heap.readArrayIndex(ref, mkSizeExpr(idx), arrayType, arrayValueSort)
        }

        assertTrue { initiallyStoredValues.all { it == arrayValueSort.sampleUValue() } }
        assertEquals(values, storedValues)
    }

    @Test
    fun testArrayLengthAfterMemset() = with(ctx) {
        val concreteAddresses = (17..30).toList()
        val values = concreteAddresses.map { mkConcreteHeapRef(it) }

        val initialSize = concreteAddresses.size * 2
        val ref = heap.allocateArray(arrayType, sizeSort, mkSizeExpr(initialSize))
        val actualInitialSize = heap.readArrayLength(ref, arrayType, sizeSort)

        heap.memset(ref, arrayType, arrayValueSort, sizeSort, values.asSequence())
        val sizeAfterMemset = heap.readArrayLength(ref, arrayType, sizeSort)

        assertEquals(mkSizeExpr(initialSize), actualInitialSize)
        assertEquals(mkSizeExpr(concreteAddresses.size), sizeAfterMemset)
    }

    @Test
    fun testAllocWithInitialsRead() = with(ctx) {
        val concreteAddresses = (17..30).toList()
        val values = concreteAddresses.map { mkConcreteHeapRef(it) }

        val ref = heap.allocateArrayInitialized(arrayType, arrayValueSort, sizeSort, values.asSequence())

        val storedValues = concreteAddresses.indices.map { idx ->
            heap.readArrayIndex(ref, mkSizeExpr(idx), arrayType, arrayValueSort)
        }

        assertEquals(values, storedValues)
    }

}
