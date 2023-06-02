package org.usvm.memory

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ksmt.expr.KExpr
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.shouldNotBeCalled
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class MapCompositionTest<Field, Type> {
    private lateinit var ctx: UContext
    private lateinit var composer: UComposer<Field, Type>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        composer = mockk()
    }

    @Test
    fun testWriteIntoEmptyTreeRegionAfterComposition() = with(ctx) {
        val concreteAddr = UConcreteHeapRef(this, address = 1)
        val symbolicAddr = addressSort.mkConst("addr")
        val value = bv32Sort.mkConst("value")

        val updatesToCompose = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
            updates = emptyRegionTree(),
            keyToRegion = {
                val singleRegion: SetRegion<KExpr<UAddressSort>> = SetRegion.singleton(concreteAddr)

                if (it == symbolicAddr) {
                    // Z \ {1}
                    SetRegion.universe<UHeapRef>().subtract(singleRegion)
                } else {
                    // {1}
                    singleRegion
                }
            },
            keyRangeToRegion = { _, _ -> error("Should not be called") },
            fullRangeRegion = { SetRegion.universe() },
            symbolicEq = { _, _ -> error("Should not be called") },
            concreteCmp = { _, _ -> error("Should not be called") },
            symbolicCmp = { _, _ -> error("Should not be called") }
        ).write(symbolicAddr, value, guard = trueExpr)

        val composer = mockk<UComposer<Field, Type>>()

        every { composer.compose(symbolicAddr) } returns concreteAddr
        every { composer.compose(value) } returns 1.toBv()
        every { composer.compose(mkTrue()) } returns mkTrue()

        val composedUpdates = updatesToCompose.map(keyMapper = { composer.compose(it) }, composer)

        assert(composedUpdates.isEmpty())
    }

    @Test
    fun testWriteIntoIntersectionAfterComposition() {
        with(ctx) {
            val fstConcreteAddr = mkConcreteHeapRef(address = 1)
            val sndConcreteAddr = mkConcreteHeapRef(address = 2)
            val thirdConcreteAddr = mkConcreteHeapRef(address = 3)

            val symbolicAddr = addressSort.mkConst("addr")

            val value = bv32Sort.mkConst("value")

            val updatesToCompose = UTreeUpdates<UHeapRef, SetRegion<UHeapRef>, UBv32Sort>(
                updates = emptyRegionTree(),
                keyToRegion = {
                    when (it) {
                        // Z \ {1, 2}
                        symbolicAddr -> {
                            SetRegion.universe<UHeapRef>().subtract(SetRegion.ofSet(fstConcreteAddr, sndConcreteAddr))
                        }
                        // {1, 2, 3}
                        thirdConcreteAddr -> SetRegion.ofSet(fstConcreteAddr, sndConcreteAddr, thirdConcreteAddr)
                        else -> SetRegion.singleton(it)
                    }
                },
                keyRangeToRegion = { _, _ -> shouldNotBeCalled() },
                fullRangeRegion = { SetRegion.universe() },
                symbolicEq = { _, _ -> shouldNotBeCalled() },
                concreteCmp = { _, _ -> shouldNotBeCalled() },
                symbolicCmp = { _, _ -> shouldNotBeCalled() }
            ).write(symbolicAddr, value, guard = trueExpr)

            val composer = mockk<UComposer<Field, Type>>()

            every { composer.compose(symbolicAddr) } returns thirdConcreteAddr
            every { composer.compose(value) } returns 1.toBv()
            every { composer.compose(mkTrue()) } returns mkTrue()

            // ComposedUpdates contains only one update in a region {3}
            val composedUpdates = updatesToCompose.map(keyMapper = { composer.compose(it) }, composer)

            assertFalse(composedUpdates.isEmpty())

            // Write in the composedUpdates by a key with estimated region {3}
            // If we'd have an initial region for the third address, it'd contain an update by region {1, 2, 3}
            // Therefore, such writings cause updates splitting. Otherwise, it contains only one update.
            val updatedByTheSameRegion = composedUpdates
                .copy(keyToRegion = { SetRegion.singleton(thirdConcreteAddr) })
                .write(thirdConcreteAddr, 42.toBv(), guard = trueExpr)

            assertNotNull(updatedByTheSameRegion.singleOrNull())
        }
    }

    @Test
    fun testPinpointUpdateMapOperationWithoutCompositionEffect() = with(ctx) {
        val key = addressSort.mkConst("key") as UExpr<UAddressSort>
        val value = bv32Sort.mkConst("value")
        val guard = boolSort.mkConst("guard")

        val updateNode = UPinpointUpdateNode(key, value, { k1, k2 -> k1 eq k2 }, guard)

        every { composer.compose(key) } returns key
        every { composer.compose(value) } returns value
        every { composer.compose(guard) } returns guard

        val mappedNode = updateNode.map({ k -> composer.compose(k) }, composer)

        assertSame(expected = updateNode, actual = mappedNode)
    }

    @Test
    fun testPinpointUpdateMapOperation() = with(ctx) {
        val key = addressSort.mkConst("key") as UExpr<UAddressSort>
        val value = bv32Sort.mkConst("value")
        val guard = boolSort.mkConst("guard")

        val updateNode = UPinpointUpdateNode(key, value, { k1, k2 -> k1 eq k2 }, guard)

        val composedKey = addressSort.mkConst("interpretedKey")

        every { composer.compose(key) } returns composedKey
        every { composer.compose(value) } returns 1.toBv()
        every { composer.compose(guard) } returns mkTrue()

        val mappedNode = updateNode.map({ k -> composer.compose(k) }, composer)

        assertNotSame(illegal = updateNode, actual = mappedNode)
        assertSame(expected = composedKey, actual = mappedNode.key)
        assertSame(expected = 1.toBv(), actual = mappedNode.value)
        assertSame(expected = mkTrue(), actual = mappedNode.guard)
    }

    @Test
    fun testRangeUpdateNodeWithoutCompositionEffect() = with(ctx) {
        val addr = addressSort.mkConst("addr")
        val fromKey = sizeSort.mkConst("fromKey") as UExpr<USizeSort>
        val toKey = sizeSort.mkConst("toKey") as UExpr<USizeSort>
        val region = mockk<USymbolicMemoryRegion<UAllocatedArrayId<Int, UBv32Sort>, UExpr<USizeSort>, UBv32Sort>>()
        val guard = boolSort.mkConst("guard")

        val updateNode = URangedUpdateNode(
            fromKey,
            toKey,
            region = region,
            concreteComparer = { _, _ -> shouldNotBeCalled() },
            symbolicComparer = { _, _ -> shouldNotBeCalled() },
            keyConverter = UAllocatedToAllocatedKeyConverter(
                srcSymbolicArrayIndex = addr to fromKey,
                dstFromSymbolicArrayIndex = addr to fromKey,
                dstToIndex = toKey
            ),
            guard
        )

        every { composer.compose(addr) } returns addr
        every { composer.compose(fromKey) } returns fromKey
        every { composer.compose(toKey) } returns toKey
        every { region.map(composer) } returns region
        every { composer.compose(guard) } returns guard

        val mappedUpdateNode = updateNode.map({ k -> composer.compose((k)) }, composer)

        assertSame(expected = updateNode, actual = mappedUpdateNode)
    }

    @Test
    fun testRangeUpdateNodeMapOperation() = with(ctx) {
        val addr = mkConcreteHeapRef(0)
        val fromKey = sizeSort.mkConst("fromKey")
        val toKey = sizeSort.mkConst("toKey")
        val region = mockk<USymbolicMemoryRegion<UAllocatedArrayId<Int, UBv32Sort>, USizeExpr, UBv32Sort>>()
        val guard = boolSort.mkConst("guard")

        val updateNode = URangedUpdateNode(
            fromKey,
            toKey,
            region = region,
            concreteComparer = { _, _ -> shouldNotBeCalled() },
            symbolicComparer = { _, _ -> shouldNotBeCalled() },
            keyConverter = UAllocatedToAllocatedKeyConverter(
                srcSymbolicArrayIndex = addr to fromKey,
                dstFromSymbolicArrayIndex = addr to fromKey,
                dstToIndex = toKey
            ),
            guard
        )

        val composedFromKey = sizeSort.mkConst("composedFromKey")
        val composedToKey = sizeSort.mkConst("composedToKey")
        val composedRegion = mockk<USymbolicMemoryRegion<UAllocatedArrayId<Int, UBv32Sort>, UExpr<USizeSort>, UBv32Sort>>()
        val composedGuard = mkTrue()

        every { composer.compose(addr) } returns addr
        every { composer.compose(fromKey) } returns composedFromKey
        every { composer.compose(toKey) } returns composedToKey
        every { region.map(composer) } returns composedRegion
        every { composer.compose(guard) } returns composedGuard

        val mappedUpdateNode = updateNode.map({ k -> composer.compose((k)) }, composer)

        assertNotSame(illegal = updateNode, actual = mappedUpdateNode)
        assertSame(expected = composedFromKey, actual = mappedUpdateNode.fromKey)
        assertSame(expected = composedToKey, actual = mappedUpdateNode.toKey)
        assertSame(expected = composedRegion, actual = mappedUpdateNode.region)
        assertSame(expected = composedGuard, actual = mappedUpdateNode.guard)
    }

    @Test
    fun testEmptyUpdatesMapOperation() {
        val emptyUpdates = UFlatUpdates<UExpr<UAddressSort>, UBv32Sort>(
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() }
        )

        val mappedUpdates = emptyUpdates.map({ k -> composer.compose(k) }, composer)

        assertSame(expected = emptyUpdates, actual = mappedUpdates)
    }

    @Test
    fun testFlatUpdatesMapOperationWithoutEffect() = with(ctx) {
        val fstKey = addressSort.mkConst("fstKey")
        val fstValue = bv32Sort.mkConst("fstValue")
        val sndKey = addressSort.mkConst("sndKey")
        val sndValue = bv32Sort.mkConst("sndValue")

        val flatUpdates = UFlatUpdates<UExpr<UAddressSort>, UBv32Sort>(
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() }
        ).write(fstKey, fstValue, guard = trueExpr)
            .write(sndKey, sndValue, guard = trueExpr)

        every { composer.compose(fstKey) } returns fstKey
        every { composer.compose(sndKey) } returns sndKey
        every { composer.compose(fstValue) } returns fstValue
        every { composer.compose(sndValue) } returns sndValue
        every { composer.compose(mkTrue()) } returns mkTrue()

        val mappedUpdates = flatUpdates.map({ k -> composer.compose(k) }, composer)

        assertSame(expected = flatUpdates, actual = mappedUpdates)
    }

    @Test
    fun testFlatUpdatesMapOperation() = with(ctx) {
        val fstKey = addressSort.mkConst("fstKey")
        val fstValue = bv32Sort.mkConst("fstValue")
        val sndKey = addressSort.mkConst("sndKey")
        val sndValue = bv32Sort.mkConst("sndValue")

        val flatUpdates = UFlatUpdates<UExpr<UAddressSort>, UBv32Sort>(
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() }
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

        val mappedUpdates = flatUpdates.map({ k -> composer.compose(k) }, composer)

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

        val treeUpdates = UTreeUpdates<UExpr<UAddressSort>, SetRegion<UExpr<UAddressSort>>, UBv32Sort>(
            emptyRegionTree(),
            { k -> SetRegion.singleton(k) },
            { k1, k2 -> SetRegion.ofSet(k1, k2) },
            { SetRegion.universe() },
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() }
        ).write(fstKey, fstValue, guard = trueExpr)
            .write(sndKey, sndValue, guard = trueExpr)

        every { composer.compose(fstKey) } returns fstKey
        every { composer.compose(sndKey) } returns sndKey
        every { composer.compose(fstValue) } returns fstValue
        every { composer.compose(sndValue) } returns sndValue
        every { composer.compose(mkTrue()) } returns mkTrue()

        val mappedUpdates = treeUpdates.map({ k -> composer.compose(k) }, composer)

        assertSame(expected = treeUpdates, actual = mappedUpdates)
    }

    @Test
    fun testTreeUpdatesMapOperation() = with(ctx) {
        val fstKey = addressSort.mkConst("fstKey")
        val fstValue = bv32Sort.mkConst("fstValue")
        val sndKey = addressSort.mkConst("sndKey")
        val sndValue = bv32Sort.mkConst("sndValue")

        val treeUpdates = UTreeUpdates<UExpr<UAddressSort>, SetRegion<UExpr<UAddressSort>>, UBv32Sort>(
            emptyRegionTree(),
            { SetRegion.universe() },
            { _, _ -> SetRegion.universe() },
            { SetRegion.universe() },
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() },
            { _, _ -> shouldNotBeCalled() }
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

        val mappedUpdates = treeUpdates.map({ k -> composer.compose(k) }, composer)

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
