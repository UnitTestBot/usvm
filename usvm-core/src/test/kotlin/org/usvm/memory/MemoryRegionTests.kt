package org.usvm.memory

import io.ksmt.utils.mkConst
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.TestKeyInfo
import org.usvm.Type
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.memory.collection.UTreeUpdates
import org.usvm.memory.collection.id.UAllocatedArrayId
import org.usvm.shouldNotBeCalled
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryRegionTests {
    private lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
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

        val memoryRegion = UAllocatedArrayId(mockk<Type>(), sizeSort, mkSizeExpr(0), 0)
            .emptyArray()
            .write(idx1, mkBv(0), trueExpr)
            .write(idx2, mkBv(1), trueExpr)

        val updatesBefore = memoryRegion.updates.toList()
        assertEquals(2, updatesBefore.size)
        assertTrue(updatesBefore.first().includesConcretely(idx1, trueExpr))
        assertTrue(updatesBefore.last().includesConcretely(idx2, trueExpr))

        val memoryRegionAfter = memoryRegion.write(idx2, mkBv(2), trueExpr)

        val updatesAfter = memoryRegionAfter.updates.toList()
        assertEquals(2, updatesAfter.size)
        assertTrue(updatesAfter.first().includesConcretely(idx1, trueExpr))
        assertTrue(updatesAfter.last().includesConcretely(idx2, trueExpr))
    }

}