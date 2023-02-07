package org.usvm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ksmt.utils.cast
import org.usvm.UAllocatedArrayMemoryRegion
import org.usvm.UAllocatedArrayReading
import org.usvm.UArrayLength
import org.usvm.UArrayLengthMemoryRegion
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFieldReading
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UInputArrayMemoryRegion
import org.usvm.UInputArrayReading
import org.usvm.UIsExpr
import org.usvm.URegisterReading
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UVectorMemoryRegion

class UContextInterningTest {
    private lateinit var context: UContext

    @BeforeEach
    fun initializeContext() {
        context = UContext()
    }

    @Test
    fun testConcreteHeapRefInterning() = with(context) {
        val firstAddress = 1
        val secondAddress = 2

        val equal = List(10) { mkConcreteHeapRef(firstAddress) }

        val createdWithoutContext = UConcreteHeapRef(this, firstAddress)
        val distinct = listOf(
            mkConcreteHeapRef(firstAddress),
            mkConcreteHeapRef(secondAddress),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testRegisterReadingInterning() = with(context) {
        val fstSort = bv16Sort
        val sndSort = bv32Sort

        val fstIndex = 1
        val sndIndex = 2

        val equal = List(10) { mkRegisterReading(fstIndex, fstSort) }

        val createdWithoutContest = URegisterReading(context, fstIndex, fstSort)
        val distinct = listOf(
            mkRegisterReading(fstIndex, fstSort),
            mkRegisterReading(fstIndex, sndSort),
            mkRegisterReading(sndIndex, fstSort),
            mkRegisterReading(sndIndex, sndSort),
            createdWithoutContest
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testFieldReadingInterning() = with(context) {
        // TODO replace after making `out` type in MemoryRegion
        val fstRegion: UVectorMemoryRegion<USort> = mockk<UVectorMemoryRegion<UBv32Sort>>().cast()
        val sndRegion: UVectorMemoryRegion<USort> = mockk<UVectorMemoryRegion<UBoolSort>>().cast()

        every { fstRegion.sort } returns bv32Sort
        every { sndRegion.sort } returns boolSort

        val fstAddress = mkConcreteHeapRef(address = 1)
        val sndAddress = mkConcreteHeapRef(address = 2)

        val fstField = mockk<java.lang.reflect.Field>() // TODO replace with JaCoDB
        val sndField = mockk<java.lang.reflect.Field>()

        val equal = List(10) { mkFieldReading(fstRegion, fstAddress, fstField) }

        val createdWithoutContext = UFieldReading(this, fstRegion, fstAddress, fstField)
        val distinct = listOf(
            mkFieldReading(fstRegion, fstAddress, fstField),
            mkFieldReading(fstRegion, fstAddress, sndField),
            mkFieldReading(fstRegion, sndAddress, fstField),
            mkFieldReading(fstRegion, sndAddress, sndField),
            mkFieldReading(sndRegion, fstAddress, fstField),
            mkFieldReading(sndRegion, fstAddress, sndField),
            mkFieldReading(sndRegion, sndAddress, fstField),
            mkFieldReading(sndRegion, sndAddress, sndField),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testAllocatedArrayReadingInterning() = with(context) {
        // TODO replace after making `out` type in regions
        val fstRegion: UAllocatedArrayMemoryRegion<USort> = mockk<UAllocatedArrayMemoryRegion<UBv32Sort>>().cast()
        val sndRegion: UAllocatedArrayMemoryRegion<USort> = mockk<UAllocatedArrayMemoryRegion<UBoolSort>>().cast()

        every { fstRegion.sort } returns bv32Sort
        every { sndRegion.sort } returns boolSort

        val fstAddress = 1
        val sndAddress = 2

        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val fstArrayType = mockk<java.lang.reflect.Field>() // TODO replace with JaCoDB
        val sndArrayType = mockk<java.lang.reflect.Field>()

        val fstElementSort = bv32Sort
        val sndElementSort = boolSort

        val equal = List(10) {
            mkAllocatedArrayReading(fstRegion, fstAddress, fstIndex, fstArrayType, fstElementSort)
        }

        val createdWithoutContext =
            UAllocatedArrayReading(this, fstRegion, fstAddress, fstIndex, fstArrayType, fstElementSort)
        val distinct = listOf(
            mkAllocatedArrayReading(fstRegion, fstAddress, fstIndex, fstArrayType, fstElementSort),
            mkAllocatedArrayReading(sndRegion, fstAddress, fstIndex, fstArrayType, sndElementSort),
            mkAllocatedArrayReading(fstRegion, sndAddress, fstIndex, fstArrayType, fstElementSort),
            mkAllocatedArrayReading(fstRegion, fstAddress, sndIndex, fstArrayType, fstElementSort),
            mkAllocatedArrayReading(fstRegion, fstAddress, fstIndex, sndArrayType, fstElementSort),
            mkAllocatedArrayReading(sndRegion, sndAddress, sndIndex, sndArrayType, sndElementSort),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testInputArrayReadingInterning() = with(context) {
        // TODO replace after making `out` type in regions
        val fstRegion: UInputArrayMemoryRegion<USort> = mockk<UInputArrayMemoryRegion<UBv32Sort>>().cast()
        val sndRegion: UInputArrayMemoryRegion<USort> = mockk<UInputArrayMemoryRegion<UBoolSort>>().cast()

        every { fstRegion.sort } returns bv32Sort
        every { sndRegion.sort } returns boolSort

        val fstAddress = mkConcreteHeapRef(address = 1)
        val sndAddress = mkConcreteHeapRef(address = 2)

        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val fstArrayType = mockk<java.lang.reflect.Field>() // TODO replace with JaCoDB
        val sndArrayType = mockk<java.lang.reflect.Field>()

        val fstElementSort = bv32Sort
        val sndElementSort = boolSort

        val equal = List(10) {
            mkInputArrayReading(fstRegion, fstAddress, fstIndex, fstArrayType, fstElementSort)
        }

        val createdWithoutContext =
            UInputArrayReading(this, fstRegion, fstAddress, fstIndex, fstArrayType, fstElementSort)
        val distinct = listOf(
            mkInputArrayReading(fstRegion, fstAddress, fstIndex, fstArrayType, fstElementSort),
            mkInputArrayReading(sndRegion, fstAddress, fstIndex, fstArrayType, sndElementSort),
            mkInputArrayReading(fstRegion, sndAddress, fstIndex, fstArrayType, fstElementSort),
            mkInputArrayReading(fstRegion, fstAddress, sndIndex, fstArrayType, fstElementSort),
            mkInputArrayReading(fstRegion, fstAddress, fstIndex, sndArrayType, fstElementSort),
            mkInputArrayReading(sndRegion, sndAddress, sndIndex, sndArrayType, sndElementSort),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }


    @Test
    fun testArrayLengthInterning() = with(context) {
        val fstRegion = mockk<UArrayLengthMemoryRegion>()
        val sndRegion = mockk<UArrayLengthMemoryRegion>()

        every { fstRegion.sort } returns sizeSort
        every { sndRegion.sort } returns sizeSort

        val fstAddress = mkConcreteHeapRef(address = 1)
        val sndAddress = mkConcreteHeapRef(address = 2)

        val fstArrayType = mockk<java.lang.reflect.Field>() // TODO replace with JaCoDB
        val sndArrayType = mockk<java.lang.reflect.Field>()

        val equal = List(10) { mkArrayLength(fstRegion, fstAddress, fstArrayType) }

        val createdWithoutContext = UArrayLength(this, fstRegion, fstAddress, fstArrayType)
        val distinct = listOf(
            mkArrayLength(fstRegion, fstAddress, fstArrayType),
            mkArrayLength(fstRegion, sndAddress, fstArrayType),
            mkArrayLength(fstRegion, fstAddress, sndArrayType),
            mkArrayLength(sndRegion, sndAddress, sndArrayType),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testIndexedMethodReturnValueInterning() = with(context) {
        val fstMethod = mockk<java.lang.reflect.Method>()
        val sndMethod = mockk<java.lang.reflect.Method>()

        val fstCallIndex = 1
        val sndCallIndex = 2

        val fstSort = bv16Sort
        val sndSort = bv32Sort

        val equal = List(10) { mkIndexedMethodReturnValue(fstMethod, fstCallIndex, fstSort) }

        val createdWithoutContext = UIndexedMethodReturnValue(this, fstMethod, fstCallIndex, fstSort)
        val distinct = listOf(
            mkIndexedMethodReturnValue(fstMethod, fstCallIndex, fstSort),
            mkIndexedMethodReturnValue(fstMethod, sndCallIndex, fstSort),
            mkIndexedMethodReturnValue(fstMethod, fstCallIndex, sndSort),
            mkIndexedMethodReturnValue(sndMethod, sndCallIndex, sndSort),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    @Test
    fun testIsExprInterning() = with(context) {
        val fstRef = mkConcreteHeapRef(address = 1)
        val sndRef = mkConcreteHeapRef(address = 2)

        val fstSort = bv16Sort // TODO replace with jacodb type
        val sndSort = bv32Sort

        val equal = List(10) { mkIsExpr(fstRef, fstSort) }

        val createdWithoutContext = UIsExpr(this, fstRef, fstSort)
        val distinct = listOf(
            mkIsExpr(fstRef, fstSort),
            mkIsExpr(fstRef, sndSort),
            mkIsExpr(sndRef, sndSort),
            createdWithoutContext
        )

        assert(compare(equal, distinct))
    }

    private fun compare(
        equals: List<UExpr<out USort>>,
        distinct: List<UExpr<out USort>>
    ): Boolean {
        val internedCorrectly = equals.all { it === equals.first() }
        val distinctWorkCorrectly = distinct.distinct().size == distinct.size

        return internedCorrectly && distinctWorkCorrectly
    }
}