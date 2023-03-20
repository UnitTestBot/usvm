package org.usvm

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ksmt.utils.mkConst
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import kotlin.test.assertNotNull

class MemoryRegionTests {
    private lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
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
                symbolicEq = { _, _ -> shouldNotBeCalled() },
                concreteCmp = { _, _ -> shouldNotBeCalled() },
                symbolicCmp = { _, _ -> shouldNotBeCalled() }
            ).write(address, 1.toBv(), guard)
                .write(address, 2.toBv(), anotherGuard)

            assert(treeUpdates.toList().size == 2)

            val anotherTreeUpdates = treeUpdates
                .write(address, 3.toBv(), anotherGuard)
                .write(address, 4.toBv(), guard)

            assert(anotherTreeUpdates.toList().size == 2)
        }
    }

}