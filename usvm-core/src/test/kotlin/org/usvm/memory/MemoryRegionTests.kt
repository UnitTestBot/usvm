package org.usvm.memory

import io.ksmt.utils.mkConst
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UHeapRef
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

            val treeUpdates = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
                updates = emptyRegionTree(),
                keyToRegion = { SetRegion.universe() },
                keyRangeToRegion = { _, _ -> shouldNotBeCalled() },
                fullRangeRegion = { SetRegion.universe() },
                symbolicEq = { _, _ -> shouldNotBeCalled() },
                concreteCmp = { _, _ -> shouldNotBeCalled() },
                symbolicCmp = { _, _ -> shouldNotBeCalled() }
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

            val treeUpdates = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
                updates = emptyRegionTree(),
                keyToRegion = { SetRegion.universe() },
                keyRangeToRegion = { _, _ -> shouldNotBeCalled() },
                fullRangeRegion = { SetRegion.universe() },
                symbolicEq = { _, _ -> shouldNotBeCalled() },
                concreteCmp = { _, _ -> shouldNotBeCalled() },
                symbolicCmp = { _, _ -> shouldNotBeCalled() }
            ).write(address, 1.toBv(), guard)
                .write(address, 2.toBv(), anotherGuard)

           assertTrue(treeUpdates.toList().size == 2)

            val anotherTreeUpdates = treeUpdates
                .write(address, 3.toBv(), anotherGuard)
                .write(address, 4.toBv(), guard)

           assertTrue(anotherTreeUpdates.toList().size == 2)
        }
    }

}