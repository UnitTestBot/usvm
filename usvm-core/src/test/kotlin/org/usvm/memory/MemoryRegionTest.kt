package org.usvm.memory

import io.ksmt.utils.mkConst
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.TestKeyInfo
import org.usvm.Type
import org.usvm.UBv32SizeExprProvider
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.collection.array.UAllocatedArrayId
import org.usvm.collection.array.UInputArrayId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.mkSizeExpr
import org.usvm.regions.SetRegion
import org.usvm.regions.emptyRegionTree
import org.usvm.sizeSort
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryRegionTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var ownership: MutabilityOwnership

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        ownership = MutabilityOwnership()
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
    }

    @Test
    fun testMultipleWriteTheSameUpdateNode() {
        /*
        This test verifies that key deduplication works as expected:
        the most recent writings for update nodes with the same key should
        override the previous ones (ignoring their value)
         */

        with(ctx) {
            val address = mkConcreteHeapRef(address = 1)

            val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
                override fun keyToRegion(key: UHeapRef): SetRegion<UHeapRef> = SetRegion.universe()
            }

            val treeUpdates = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
                updates = emptyRegionTree(),
                keyInfo
            ).write(address, 1.toBv(), mkTrue())
                .write(address, 2.toBv(), mkTrue())
                .write(address, 3.toBv(), mkTrue())
                .write(address, 4.toBv(), mkTrue())
                .write(address, 5.toBv(), mkTrue())

            assertNotNull(treeUpdates.singleOrNull())
        }
    }

    @Test
    fun testMultipleWriteTheSameNodesWithDifferentGuards() {
        with(ctx) {
            val address = mkConcreteHeapRef(address = 1)

            val guard = boolSort.mkConst("boolConst")
            val anotherGuard = boolSort.mkConst("anotherBoolConst")

            val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
                override fun keyToRegion(key: UHeapRef): SetRegion<UHeapRef> = SetRegion.universe()
            }

            val treeUpdates = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
                updates = emptyRegionTree(),
                keyInfo
            ).write(address, 1.toBv(), guard)
                .write(address, 2.toBv(), anotherGuard)

           assertEquals(2, treeUpdates.toList().size)

            val anotherTreeUpdates = treeUpdates
                .write(address, 3.toBv(), anotherGuard)
                .write(address, 4.toBv(), guard)

            assertEquals(3, anotherTreeUpdates.toList().size)
        }
    }

    @Test
    fun testKeyFiltering() = with(ctx) {
        val idx1 = mkRegisterReading(0, sizeSort)
        val idx2 = mkRegisterReading(1, sizeSort)

        val memoryRegion = UAllocatedArrayId<_, _, USizeSort>(mockk<Type>(), sizeSort, 0)
            .emptyRegion()
            .write(idx1, mkBv(0), trueExpr, ownership)
            .write(idx2, mkBv(1), trueExpr, ownership)

        val updatesBefore = memoryRegion.updates.toList()
        assertEquals(2, updatesBefore.size)
        assertTrue(updatesBefore.first().includesConcretely(idx1, trueExpr))
        assertTrue(updatesBefore.last().includesConcretely(idx2, trueExpr))

        val memoryRegionAfter = memoryRegion.write(idx2, mkBv(2), trueExpr, ownership)

        val updatesAfter = memoryRegionAfter.updates.toList()
        assertEquals(2, updatesAfter.size)
        assertTrue(updatesAfter.first().includesConcretely(idx1, trueExpr))
        assertTrue(updatesAfter.last().includesConcretely(idx2, trueExpr))
    }

    /**
     * Tests random writes and reads with array region to ensure there are no REs.
     */
    @Test
    fun testSymbolicWrites(): Unit = with(ctx) {
        val random = Random(42)
        val range = 3

        val concreteRefs = List(range) { mkConcreteHeapRef(it) }
        val symbolicRefs = List(range) { mkRegisterReading(it, addressSort) }
        val refs = concreteRefs + symbolicRefs

        val concreteIndices = List(range) { mkSizeExpr(it) }
        val symbolicIndices = List(range) { mkRegisterReading(it, sizeSort) }
        val indices = concreteIndices + symbolicIndices

        val testsCount = 100
        repeat(testsCount) {
            var memoryRegion = UInputArrayId<_, _, USizeSort>(mockk<Type>(), addressSort)
                .emptyRegion()

            val writesCount = 20
            repeat(writesCount) {
                val ref = symbolicRefs.random(random)
                val idx = indices.random(random)
                val value = refs.random(random)

                memoryRegion = memoryRegion.write(ref to idx, value, trueExpr, ownership)
            }

            val readRef = symbolicRefs.random(random)
            val readIdx = indices.random(random)

            memoryRegion.read(readRef to readIdx)
        }
    }
}
