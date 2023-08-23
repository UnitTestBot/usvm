package org.usvm.memory

import io.ksmt.expr.KExpr
import io.ksmt.utils.mkConst
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.TestKeyInfo
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.collection.array.USymbolicArrayAllocatedToAllocatedCopyAdapter
import org.usvm.collection.array.USymbolicArrayCopyAdapter
import org.usvm.collection.array.UAllocatedArrayId
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapCompositionTest<Type> {
    private lateinit var ctx: UContext
    private lateinit var composer: UComposer<Type>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        composer = mockk()
    }

    @Test
    @Disabled("Non clear")
    fun testWriteIntoEmptyTreeRegionAfterComposition() = with(ctx) {
        val concreteAddr = UConcreteHeapRef(this, address = 1)
        val symbolicAddr = addressSort.mkConst("addr")
        val value = bv32Sort.mkConst("value")

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
            override fun keyToRegion(key: UHeapRef): SetRegion<UHeapRef> {
                val singleRegion: SetRegion<KExpr<UAddressSort>> = SetRegion.singleton(concreteAddr)
                return if (key == symbolicAddr) {
                    // Z \ {1}
                    SetRegion.universe<UHeapRef>().subtract(singleRegion)
                } else {
                    // {1}
                    singleRegion
                }
            }
        }

        val updatesToCompose = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
            updates = emptyRegionTree(),
            keyInfo = keyInfo
        ).write(symbolicAddr, value, guard = trueExpr)

        val composer = mockk<UComposer<Type>>()

        every { composer.compose(symbolicAddr) } returns concreteAddr
        every { composer.compose(value) } returns 1.toBv()
        every { composer.compose(mkTrue()) } returns mkTrue()

        val composedUpdates = updatesToCompose.filterMap(keyMapper = { composer.compose(it) }, composer, keyInfo)

        // Why should updates be empty after composition?
        assertTrue(composedUpdates.isEmpty())
    }

    @Test
    fun testWriteIntoIntersectionAfterComposition() {
        with(ctx) {
            val fstConcreteAddr = mkConcreteHeapRef(address = 1)
            val sndConcreteAddr = mkConcreteHeapRef(address = 2)
            val thirdConcreteAddr = mkConcreteHeapRef(address = 3)

            val symbolicAddr = addressSort.mkConst("addr")

            val value = bv32Sort.mkConst("value")

            val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
                override fun keyToRegion(key: UHeapRef): SetRegion<UHeapRef> {
                    return when (key) {
                        // Z \ {1, 2}
                        symbolicAddr -> {
                            SetRegion.universe<UHeapRef>().subtract(SetRegion.ofSet(fstConcreteAddr, sndConcreteAddr))
                        }
                        // {1, 2, 3}
                        thirdConcreteAddr -> SetRegion.ofSet(fstConcreteAddr, sndConcreteAddr, thirdConcreteAddr)
                        else -> SetRegion.singleton(key)
                    }
                }
            }

            val updatesToCompose = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
                updates = emptyRegionTree(),
                keyInfo
            ).write(symbolicAddr, value, guard = trueExpr)

            val composer = mockk<UComposer<Type>>()

            every { composer.compose(symbolicAddr) } returns thirdConcreteAddr
            every { composer.compose(value) } returns 1.toBv()
            every { composer.compose(mkTrue()) } returns mkTrue()

            // ComposedUpdates contains only one update in a region {3}
            val composedUpdates = updatesToCompose.filterMap(keyMapper = { composer.compose(it) }, composer, keyInfo)

            assertFalse(composedUpdates.isEmpty())

            // Write in the composedUpdates by a key with estimated region {3}
            // If we'd have an initial region for the third address, it'd contain an update by region {1, 2, 3}
            // Therefore, such writings cause updates splitting. Otherwise, it contains only one update.
            val updatedKeyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
                override fun keyToRegion(key: UHeapRef): SetRegion<UHeapRef> =
                    SetRegion.singleton(thirdConcreteAddr)
            }

            val updatedByTheSameRegion = composedUpdates
                .copy(keyInfo = updatedKeyInfo)
                .write(thirdConcreteAddr, 42.toBv(), guard = trueExpr)

            assertNotNull(updatedByTheSameRegion.singleOrNull())
        }
    }

    @Test
    fun testPinpointUpdateMapOperationWithoutCompositionEffect() = with(ctx) {
        val key = addressSort.mkConst("key") as UExpr<UAddressSort>
        val value = bv32Sort.mkConst("value")
        val guard = boolSort.mkConst("guard")

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
            override fun eqSymbolic(ctx: UContext, key1: UHeapRef, key2: UHeapRef): UBoolExpr = key1 eq key2
        }

        val updateNode = UPinpointUpdateNode(key, keyInfo, value, guard)

        every { composer.compose(key) } returns key
        every { composer.compose(value) } returns value
        every { composer.compose(guard) } returns guard

        val mappedNode = updateNode.map({ k -> composer.compose(k) }, composer, keyInfo)

        assertSame(expected = updateNode, actual = mappedNode)
    }

    @Test
    fun testPinpointUpdateMapOperation() = with(ctx) {
        val key = addressSort.mkConst("key") as UExpr<UAddressSort>
        val value = bv32Sort.mkConst("value")
        val guard = boolSort.mkConst("guard")

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
            override fun eqSymbolic(ctx: UContext, key1: UHeapRef, key2: UHeapRef): UBoolExpr = key1 eq key2
        }

        val updateNode = UPinpointUpdateNode(key, keyInfo, value, guard)

        val composedKey = addressSort.mkConst("interpretedKey")

        every { composer.compose(key) } returns composedKey
        every { composer.compose(value) } returns 1.toBv()
        every { composer.compose(guard) } returns mkTrue()

        val mappedNode = updateNode.map({ k -> composer.compose(k) }, composer, keyInfo)

        assertNotSame(illegal = updateNode, actual = mappedNode)
        assertSame(expected = composedKey, actual = mappedNode?.key)
        assertSame(expected = 1.toBv(), actual = mappedNode?.value)
        assertSame(expected = mkTrue(), actual = mappedNode?.guard)
    }

    @Test
    fun testRangeUpdateNodeWithoutCompositionEffect() = with(ctx) {
        val fromKey = sizeSort.mkConst("fromKey") as UExpr<USizeSort>
        val toKey = sizeSort.mkConst("toKey") as UExpr<USizeSort>
        val guard = boolSort.mkConst("guard")

        val keyInfo = object : TestKeyInfo<USizeExpr, SetRegion<USizeExpr>> {
        }

        val region = UAllocatedArrayId(Unit, bv32Sort, address = 1).emptyRegion()
        val updateNode = URangedUpdateNode(
            region,
            USymbolicArrayAllocatedToAllocatedCopyAdapter(fromKey, fromKey, toKey, keyInfo),
            guard
        )

        every { composer.compose(fromKey) } returns fromKey
        every { composer.apply(fromKey) } returns fromKey

        every { composer.compose(toKey) } returns toKey
        every { composer.apply(toKey) } returns toKey

        every { composer.compose(guard) } returns guard

        every { composer.compose(mkBv(0)) } returns mkBv(0)
        every { composer.memory } returns UMemory<Type, Any>(ctx, mockk())

        val mappedUpdateNode = updateNode.map({ k -> composer.compose((k)) }, composer, keyInfo)

        // Region.contextMemory changed after composition
        assertEquals(expected = updateNode, actual = mappedUpdateNode)
    }

    @Test
    fun testRangeUpdateNodeMapOperation() = with(ctx) {
        val addr = mkConcreteHeapRef(0)
        val fromKey = sizeSort.mkConst("fromKey")
        val toKey = sizeSort.mkConst("toKey")
        val guard = boolSort.mkConst("guard")

        val keyInfo = object : TestKeyInfo<USizeExpr, SetRegion<USizeExpr>> {
        }

        val region = UAllocatedArrayId(Unit, bv32Sort, addr.address).emptyRegion()
        val updateNode = URangedUpdateNode(
            region,
            USymbolicArrayAllocatedToAllocatedCopyAdapter(fromKey, fromKey, toKey, keyInfo),
            guard
        )

        val composedFromKey = sizeSort.mkConst("composedFromKey")
        val composedToKey = sizeSort.mkConst("composedToKey")
        val composedGuard = mkTrue()

        every { composer.compose(addr) } returns addr

        every { composer.compose(fromKey) } returns composedFromKey
        every { composer.apply(fromKey) } returns composedFromKey

        every { composer.compose(toKey) } returns composedToKey
        every { composer.apply(toKey) } returns composedToKey

        every { composer.compose(guard) } returns composedGuard
        every { composer.compose(mkBv(0)) } returns mkBv(0)
        every { composer.memory } returns UMemory<Type, Any>(ctx, mockk())

        val mappedUpdateNode = updateNode.map({ k -> composer.compose((k)) }, composer, keyInfo)

        assertNotSame(illegal = updateNode, actual = mappedUpdateNode)
        assertSame(
            expected = composedFromKey,
            actual = (mappedUpdateNode?.adapter as? USymbolicArrayCopyAdapter<*, *>)?.dstFrom
        )
        assertSame(
            expected = composedToKey,
            actual = (mappedUpdateNode?.adapter as? USymbolicArrayCopyAdapter<*, *>)?.dstTo
        )
        assertSame(expected = composedGuard, actual = mappedUpdateNode?.guard)

        // Region.contextMemory changed after composition
        assertEquals(expected = region, actual = mappedUpdateNode?.sourceCollection)
    }

    @Test
    fun testEmptyUpdatesMapOperation() {
        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
        }

        val emptyUpdates = UFlatUpdates<UExpr<UAddressSort>, UBv32Sort>(keyInfo)

        val mappedUpdates = emptyUpdates.filterMap({ k -> composer.compose(k) }, composer, keyInfo)

        assertSame(expected = emptyUpdates, actual = mappedUpdates)
    }

    @Test
    fun testFlatUpdatesMapOperationWithoutEffect() = with(ctx) {
        val fstKey = addressSort.mkConst("fstKey")
        val fstValue = bv32Sort.mkConst("fstValue")
        val sndKey = addressSort.mkConst("sndKey")
        val sndValue = bv32Sort.mkConst("sndValue")

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
        }

        val flatUpdates = UFlatUpdates<UExpr<UAddressSort>, UBv32Sort>(keyInfo)
            .write(fstKey, fstValue, guard = trueExpr)
            .write(sndKey, sndValue, guard = trueExpr)

        every { composer.compose(fstKey) } returns fstKey
        every { composer.compose(sndKey) } returns sndKey
        every { composer.compose(fstValue) } returns fstValue
        every { composer.compose(sndValue) } returns sndValue
        every { composer.compose(mkTrue()) } returns mkTrue()

        val mappedUpdates = flatUpdates.filterMap({ k -> composer.compose(k) }, composer, keyInfo)

        assertSame(expected = flatUpdates, actual = mappedUpdates)
    }

    @Test
    fun testFlatUpdatesMapOperation() = with(ctx) {
        val fstKey = addressSort.mkConst("fstKey")
        val fstValue = bv32Sort.mkConst("fstValue")
        val sndKey = addressSort.mkConst("sndKey")
        val sndValue = bv32Sort.mkConst("sndValue")

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
        }

        val flatUpdates = UFlatUpdates<UExpr<UAddressSort>, UBv32Sort>(keyInfo)
            .write(fstKey, fstValue, guard = trueExpr)
            .write(sndKey, sndValue, guard = trueExpr)

        val composedFstKey = addressSort.mkConst("composedFstKey")
        val composedSndKey = addressSort.mkConst("composedSndKey")
        val composedFstValue = 1.toBv()
        val composedSndValue = 2.toBv()

        every { composer.compose(fstKey) } returns composedFstKey
        every { composer.compose(sndKey) } returns composedSndKey
        every { composer.compose(fstValue) } returns composedFstValue
        every { composer.compose(sndValue) } returns composedSndValue
        every { composer.compose(mkTrue()) } returns mkTrue()

        val mappedUpdates = flatUpdates.filterMap({ k -> composer.compose(k) }, composer, keyInfo)

        assertNotSame(illegal = flatUpdates, actual = mappedUpdates)

        val node = mappedUpdates.node?.update as UPinpointUpdateNode<*, *>
        val next = mappedUpdates.node.next as UFlatUpdates<*, *>
        val nextNode = next.node?.update as UPinpointUpdateNode<*, *>

        assertSame(expected = composedSndKey, actual = node.key)
        assertSame(expected = composedSndValue, actual = node.value)
        assertSame(expected = composedFstKey, actual = nextNode.key)
        assertSame(expected = composedFstValue, actual = nextNode.value)
        assertNull(next.node.next.node)
    }

    @Test
    fun testTreeUpdatesMapOperationWithoutEffect() = with(ctx) {
        val fstKey = addressSort.mkConst("fstKey")
        val fstValue = bv32Sort.mkConst("fstValue")
        val sndKey = addressSort.mkConst("sndKey")
        val sndValue = bv32Sort.mkConst("sndValue")

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
            override fun keyToRegion(key: UHeapRef): SetRegion<UHeapRef> = SetRegion.singleton(key)
            override fun keyRangeRegion(from: UHeapRef, to: UHeapRef): SetRegion<UHeapRef> =
                SetRegion.ofSet(from, to)
        }

        val treeUpdates = UTreeUpdates<UExpr<UAddressSort>, SetRegion<UExpr<UAddressSort>>, UBv32Sort>(
            emptyRegionTree(),
            keyInfo
        ).write(fstKey, fstValue, guard = trueExpr)
            .write(sndKey, sndValue, guard = trueExpr)

        every { composer.compose(fstKey) } returns fstKey
        every { composer.compose(sndKey) } returns sndKey
        every { composer.compose(fstValue) } returns fstValue
        every { composer.compose(sndValue) } returns sndValue
        every { composer.compose(mkTrue()) } returns mkTrue()

        val mappedUpdates = treeUpdates.filterMap({ k -> composer.compose(k) }, composer, keyInfo)

        assertSame(expected = treeUpdates, actual = mappedUpdates)
    }

    @Test
    fun testTreeUpdatesMapOperation() = with(ctx) {
        val fstKey = addressSort.mkConst("fstKey")
        val fstValue = bv32Sort.mkConst("fstValue")
        val sndKey = addressSort.mkConst("sndKey")
        val sndValue = bv32Sort.mkConst("sndValue")

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
            override fun keyToRegion(key: UHeapRef): SetRegion<UHeapRef> = SetRegion.universe()
            override fun keyRangeRegion(from: UHeapRef, to: UHeapRef): SetRegion<UHeapRef> = SetRegion.universe()
        }

        val treeUpdates = UTreeUpdates<UExpr<UAddressSort>, SetRegion<UExpr<UAddressSort>>, UBv32Sort>(
            emptyRegionTree(),
            keyInfo
        ).write(fstKey, fstValue, guard = trueExpr)
            .write(sndKey, sndValue, guard = trueExpr)

        val composedFstKey = addressSort.mkConst("composedFstKey")
        val composedSndKey = addressSort.mkConst("composedSndKey")
        val composedFstValue = 1.toBv()
        val composedSndValue = 2.toBv()

        every { composer.compose(fstKey) } returns composedFstKey
        every { composer.compose(sndKey) } returns composedSndKey
        every { composer.compose(fstValue) } returns composedFstValue
        every { composer.compose(sndValue) } returns composedSndValue
        every { composer.compose(mkTrue()) } returns mkTrue()

        val mappedUpdates = treeUpdates.filterMap({ k -> composer.compose(k) }, composer, keyInfo)

        assertNotSame(illegal = treeUpdates, actual = mappedUpdates)

        val elements = mappedUpdates.toList()

        assert(elements.size == 2)

        val fstElement = elements.first() as UPinpointUpdateNode<*, *>
        val sndElement = elements.last() as UPinpointUpdateNode<*, *>

        assertSame(expected = composedFstKey, actual = fstElement.key)
        assertSame(expected = composedFstValue, actual = fstElement.value)
        assertSame(expected = composedSndKey, actual = sndElement.key)
        assertSame(expected = composedSndValue, actual = sndElement.value)
    }
}
