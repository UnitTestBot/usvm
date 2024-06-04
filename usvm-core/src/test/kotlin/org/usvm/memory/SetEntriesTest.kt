package org.usvm.memory

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.Type
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.api.refSetAddElement
import org.usvm.api.refSetRemoveElement
import org.usvm.api.setAddElement
import org.usvm.api.setRemoveElement
import org.usvm.collection.set.length.UInputWithInputSymbolicSetIntersectionSize
import org.usvm.collection.set.primitive.setEntries
import org.usvm.collection.set.primitive.setIntersectionSize
import org.usvm.collection.set.primitive.setUnion
import org.usvm.collection.set.ref.refSetEntries
import org.usvm.collection.set.ref.refSetIntersectionSize
import org.usvm.collection.set.ref.refSetUnion
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.isTrue
import org.usvm.memory.key.USizeExprKeyInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SetEntriesTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var heap: UMemory<Type, Any>
    private lateinit var setType: Type

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        val eqConstraints = UEqualityConstraints(ctx)
        val typeConstraints = UTypeConstraints(components.mkTypeSystem(ctx), eqConstraints)
        heap = UMemory(ctx, typeConstraints)
        setType = mockk<Type>()
    }

    @Test
    fun testSetEntries(): Unit = with(ctx) {
        val setRef1 = mkRegisterReading(0, addressSort)
        val setRef2 = mkRegisterReading(1, addressSort)

        heap.setAddElement(setRef1, mkBv(0), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setAddElement(setRef1, mkBv(1), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setRemoveElement(setRef1, mkBv(2), setType, USizeExprKeyInfo(), guard = trueExpr)

        heap.setAddElement(setRef2, mkBv(3), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setAddElement(setRef2, mkBv(4), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setRemoveElement(setRef2, mkBv(1), setType, USizeExprKeyInfo(), guard = trueExpr)

        heap.setUnion(setRef2, setRef1, setType, bv32Sort, USizeExprKeyInfo(), guard = trueExpr)

        val setEntries = heap.setEntries(setRef1, setType, bv32Sort, USizeExprKeyInfo())
        assertTrue(setEntries.isInput)

        val expectedElements: Set<UExpr<USizeSort>> = (0..4).mapTo(hashSetOf()) { mkBv(it) }
        val actualElements = setEntries.entries.mapTo(hashSetOf()) { it.setElement }
        assertEquals(expectedElements, actualElements)

        // todo: mkBv(0) not in set because of union
        val expectedDefInSet = setOf<UExpr<USizeSort>>(mkBv(3), mkBv(4))

        val definitelyInSet = setEntries.entries.filter { heap.read(it).isTrue }.mapTo(hashSetOf()) { it.setElement }
        assertEquals(expectedDefInSet, definitelyInSet)
    }

    @Test
    fun testRefSetEntries(): Unit = with(ctx) {
        val setRef1 = mkRegisterReading(0, addressSort)
        val setRef2 = mkRegisterReading(1, addressSort)

        val refs = List(5) { mkConcreteHeapRef(addressCounter.freshStaticAddress()) }

        heap.refSetAddElement(setRef1, refs[0], setType, guard = trueExpr)
        heap.refSetAddElement(setRef1, refs[1], setType, guard = trueExpr)
        heap.refSetRemoveElement(setRef1, refs[2], setType, guard = trueExpr)

        heap.refSetAddElement(setRef2, refs[3], setType, guard = trueExpr)
        heap.refSetAddElement(setRef2, refs[4], setType, guard = trueExpr)
        heap.refSetRemoveElement(setRef2, refs[1], setType, guard = trueExpr)

        heap.refSetUnion(setRef2, setRef1, setType, guard = trueExpr)

        val setEntries = heap.refSetEntries(setRef1, setType)
        assertTrue(setEntries.isInput)

        val actualElements = setEntries.entries.mapTo(hashSetOf()) { it.setElement }
        assertEquals(refs.toSet() as Set<UHeapRef>, actualElements)

        // todo: refs[0] not in set because of union
        val expectedDefInSet = setOf<UHeapRef>(refs[3], refs[4])

        val definitelyInSet = setEntries.entries.filter { heap.read(it).isTrue }.mapTo(hashSetOf()) { it.setElement }
        assertEquals(expectedDefInSet, definitelyInSet)
    }

    @Test
    fun testAllocatedSetIntersectionSize(): Unit = with(ctx) {
        val setRef1 = mkConcreteHeapRef(0)
        val setRef2 = mkConcreteHeapRef(1)

        heap.setAddElement(setRef1, mkBv(0), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setAddElement(setRef1, mkBv(1), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setRemoveElement(setRef1, mkBv(2), setType, USizeExprKeyInfo(), guard = trueExpr)

        heap.setAddElement(setRef2, mkBv(3), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setAddElement(setRef2, mkBv(4), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setRemoveElement(setRef2, mkBv(1), setType, USizeExprKeyInfo(), guard = trueExpr)

        val intersectionSize = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef1, setRef2, setType, bv32Sort, USizeExprKeyInfo()
        )
        val swappedIntersectionSize = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef2, setRef1, setType, bv32Sort, USizeExprKeyInfo()
        )

        heap.setUnion(setRef2, setRef1, setType, bv32Sort, USizeExprKeyInfo(), guard = trueExpr)

        val intersectionSizeAfterUnion = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef1, setRef2, setType, bv32Sort, USizeExprKeyInfo()
        )
        val swappedIntersectionSizeAfterUnion = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef2, setRef1, setType, bv32Sort, USizeExprKeyInfo()
        )

        assertEquals(mkBv(0), intersectionSize)
        assertEquals(mkBv(0), swappedIntersectionSize)

        assertEquals(mkBv(2), intersectionSizeAfterUnion)
        assertEquals(mkBv(2), swappedIntersectionSizeAfterUnion)
    }

    @Test
    fun testInputSetIntersectionSize(): Unit = with(ctx) {
        val setRef1 = mkRegisterReading(0, addressSort)
        val setRef2 = mkRegisterReading(1, addressSort)

        heap.setAddElement(setRef1, mkBv(0), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setAddElement(setRef1, mkBv(1), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setRemoveElement(setRef1, mkBv(2), setType, USizeExprKeyInfo(), guard = trueExpr)

        heap.setAddElement(setRef2, mkBv(3), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setAddElement(setRef2, mkBv(4), setType, USizeExprKeyInfo(), guard = trueExpr)
        heap.setRemoveElement(setRef2, mkBv(1), setType, USizeExprKeyInfo(), guard = trueExpr)

        val intersectionSize = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef1, setRef2, setType, bv32Sort, USizeExprKeyInfo()
        )
        val swappedIntersectionSize = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef2, setRef1, setType, bv32Sort, USizeExprKeyInfo()
        )

        heap.setUnion(setRef2, setRef1, setType, bv32Sort, USizeExprKeyInfo(), guard = trueExpr)

        val intersectionSizeAfterUnion = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef1, setRef2, setType, bv32Sort, USizeExprKeyInfo()
        )
        val swappedIntersectionSizeAfterUnion = heap.setIntersectionSize<_, _, _, USizeSort>(
            setRef2, setRef1, setType, bv32Sort, USizeExprKeyInfo()
        )

        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(intersectionSize)
        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(swappedIntersectionSize)

        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(intersectionSizeAfterUnion)
        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(swappedIntersectionSizeAfterUnion)

        val memory = UMemory<Any, Any>(ctx, mockk())
        memory.stack.push(2)
        memory.stack.writeRegister(0, mkConcreteHeapRef(0))
        memory.stack.writeRegister(1, mkConcreteHeapRef(1))

        val composer = UComposer(ctx, memory)

        assertEquals(mkBv(0), composer.compose(intersectionSize))
        assertEquals(mkBv(0), composer.compose(swappedIntersectionSize))

        assertEquals(mkBv(2), composer.compose(intersectionSizeAfterUnion))
        assertEquals(mkBv(2), composer.compose(swappedIntersectionSizeAfterUnion))
    }

    @Test
    fun testAllocatedRefSetIntersectionSize(): Unit = with(ctx) {
        val setRef1 = mkConcreteHeapRef(0)
        val setRef2 = mkConcreteHeapRef(1)

        val refs = List(5) { mkConcreteHeapRef(addressCounter.freshStaticAddress()) }

        heap.refSetAddElement(setRef1, refs[0], setType, guard = trueExpr)
        heap.refSetAddElement(setRef1, refs[1], setType, guard = trueExpr)
        heap.refSetRemoveElement(setRef1, refs[2], setType, guard = trueExpr)

        heap.refSetAddElement(setRef2, refs[3], setType, guard = trueExpr)
        heap.refSetAddElement(setRef2, refs[4], setType, guard = trueExpr)
        heap.refSetRemoveElement(setRef2, refs[1], setType, guard = trueExpr)

        val intersectionSize = heap.refSetIntersectionSize<_, USizeSort>(setRef1, setRef2, setType)
        val swappedIntersectionSize = heap.refSetIntersectionSize<_, USizeSort>(setRef2, setRef1, setType)

        heap.refSetUnion(setRef2, setRef1, setType, guard = trueExpr)

        val intersectionSizeAfterUnion = heap.refSetIntersectionSize<_, USizeSort>(setRef1, setRef2, setType)
        val swappedIntersectionSizeAfterUnion = heap.refSetIntersectionSize<_, USizeSort>(setRef2, setRef1, setType)

        assertEquals(mkBv(0), intersectionSize)
        assertEquals(mkBv(0), swappedIntersectionSize)

        assertEquals(mkBv(2), intersectionSizeAfterUnion)
        assertEquals(mkBv(2), swappedIntersectionSizeAfterUnion)
    }

    @Test
    fun testInputRefSetIntersectionSize(): Unit = with(ctx) {
        val setRef1 = mkRegisterReading(0, addressSort)
        val setRef2 = mkRegisterReading(1, addressSort)

        val refs = List(5) { mkConcreteHeapRef(addressCounter.freshStaticAddress()) }

        heap.refSetAddElement(setRef1, refs[0], setType, guard = trueExpr)
        heap.refSetAddElement(setRef1, refs[1], setType, guard = trueExpr)
        heap.refSetRemoveElement(setRef1, refs[2], setType, guard = trueExpr)

        heap.refSetAddElement(setRef2, refs[3], setType, guard = trueExpr)
        heap.refSetAddElement(setRef2, refs[4], setType, guard = trueExpr)
        heap.refSetRemoveElement(setRef2, refs[1], setType, guard = trueExpr)

        val intersectionSize = heap.refSetIntersectionSize<_, USizeSort>(setRef1, setRef2, setType)
        val swappedIntersectionSize = heap.refSetIntersectionSize<_, USizeSort>(setRef2, setRef1, setType)

        heap.refSetUnion(setRef2, setRef1, setType, guard = trueExpr)

        val intersectionSizeAfterUnion = heap.refSetIntersectionSize<_, USizeSort>(setRef1, setRef2, setType)
        val swappedIntersectionSizeAfterUnion = heap.refSetIntersectionSize<_, USizeSort>(setRef2, setRef1, setType)

        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(intersectionSize)
        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(swappedIntersectionSize)

        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(intersectionSizeAfterUnion)
        assertIs<UInputWithInputSymbolicSetIntersectionSize<*, *, *>>(swappedIntersectionSizeAfterUnion)

        val memory = UMemory<Any, Any>(ctx, mockk())
        memory.stack.push(2)
        memory.stack.writeRegister(0, mkConcreteHeapRef(0))
        memory.stack.writeRegister(1, mkConcreteHeapRef(1))

        val composer = UComposer(ctx, memory)

        assertEquals(mkBv(0), composer.compose(intersectionSize))
        assertEquals(mkBv(0), composer.compose(swappedIntersectionSize))

        assertEquals(mkBv(2), composer.compose(intersectionSizeAfterUnion))
        assertEquals(mkBv(2), composer.compose(swappedIntersectionSizeAfterUnion))
    }
}
