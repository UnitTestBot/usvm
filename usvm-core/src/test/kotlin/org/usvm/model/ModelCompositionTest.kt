package org.usvm.model

import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.*
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UInputToAllocatedKeyConverter
import org.usvm.memory.emptyAllocatedArrayCollection
import org.usvm.memory.emptyInputArrayLengthCollection
import org.usvm.memory.emptyInputArrayCollection
import org.usvm.memory.emptyInputFieldCollection
import kotlin.test.assertSame

class ModelCompositionTest {
    private lateinit var ctx: UContext
    private lateinit var concreteNull: UConcreteHeapRef

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        concreteNull = ctx.mkConcreteHeapRef(UAddressCounter.NULL_ADDRESS)
    }

    @Test
    fun testComposeAllocatedArray() = with(ctx) {
        val heapEvaluator = UHeapEagerModel<Field, Type>(
            concreteNull,
            mapOf(),
            mapOf(),
            mapOf(),
            mapOf(),
            mapOf(),
        )

        val stackModel = URegistersStackEagerModel(concreteNull, mapOf(0 to ctx.mkBv(0), 1 to ctx.mkBv(0), 2 to ctx.mkBv(2)))

        val composer = UComposer(this, stackModel, heapEvaluator, mockk(), mockk())

        val region = emptyAllocatedArrayCollection<Type, UBv32Sort>(mockk(), 1, bv32Sort)
            .write(0.toBv(), 0.toBv(), trueExpr)
            .write(1.toBv(), 1.toBv(), trueExpr)
            .write(mkRegisterReading(1, sizeSort), 2.toBv(), trueExpr)
            .write(mkRegisterReading(2, sizeSort), 3.toBv(), trueExpr)
        val reading = region.read(mkRegisterReading(0, sizeSort))

        val expr = composer.compose(reading)
        assertSame(mkBv(2), expr)
    }

    @Test
    fun testComposeRangedUpdate() = with(ctx) {
        val arrayType = mockk<Type>()
        val composedSymbolicHeapRef = ctx.mkConcreteHeapRef(-1)
        val inputArray = UMemory2DArray(persistentMapOf((composedSymbolicHeapRef to mkBv(0)) to mkBv(1)), mkBv(0))
        val heapEvaluator = UHeapEagerModel<Field, Type>(
            concreteNull,
            mapOf(),
            mapOf(arrayType to inputArray),
            mapOf(),
            mapOf(),
            mapOf(),
        )

        val stackModel =
            URegistersStackEagerModel(concreteNull, mapOf(0 to composedSymbolicHeapRef, 1 to mkBv(0)))
        val composer = UComposer(this, stackModel, heapEvaluator, mockk(), mockk())

        val symbolicRef = mkRegisterReading(0, addressSort)

        val fromRegion = emptyInputArrayCollection(arrayType, bv32Sort)

        val concreteRef = mkConcreteHeapRef(1)

        val keyConverter = UInputToAllocatedKeyConverter(symbolicRef to mkBv(0), concreteRef to mkBv(0), mkBv(5))
        val concreteRegion = emptyAllocatedArrayCollection(arrayType, concreteRef.address, bv32Sort)
            .copyRange(fromRegion, mkBv(0), mkBv(5), keyConverter, trueExpr)

        val idx = mkRegisterReading(1, sizeSort)

        val reading = concreteRegion.read(idx)

        val expr = composer.compose(reading)
        assertSame(mkBv(1), expr)
    }

    @Test
    fun testComposeInputArrayLength() = with(ctx) {
        val symbolicRef0 = mkRegisterReading(0, addressSort)
        val symbolicRef1 = mkRegisterReading(1, addressSort)
        val symbolicRef2 = mkRegisterReading(2, addressSort)
        val symbolicRef3 = mkRegisterReading(3, addressSort)

        val composedRef0 = mkConcreteHeapRef(-1)
        val composedRef1 = mkConcreteHeapRef(-2)
        val composedRef2 = mkConcreteHeapRef(-3)
        val composedRef3 = mkConcreteHeapRef(-4)

        val arrayType = mockk<Type>()
        val inputLength = UMemory1DArray(persistentMapOf(composedRef0 to mkBv(42)), mkBv(0))
        val heapEvaluator = UHeapEagerModel<Field, Type>(
            concreteNull,
            mapOf(),
            mapOf(),
            mapOf(arrayType to inputLength),
            mapOf(),
            mapOf(),
        )

        val stackModel = URegistersStackEagerModel(
            concreteNull,
            mapOf(
                0 to composedRef0,
                1 to composedRef1,
                2 to composedRef2,
                3 to composedRef3
            )
        )

        val composer = UComposer(this, stackModel, heapEvaluator, mockk(), mockk())

        val region = emptyInputArrayLengthCollection(arrayType, bv32Sort)
            .write(symbolicRef1, 0.toBv(), trueExpr)
            .write(symbolicRef2, 1.toBv(), trueExpr)
            .write(symbolicRef3, 2.toBv(), trueExpr)
        val reading = region.read(symbolicRef0)

        val expr = composer.compose(reading)
        assertSame(mkBv(42), expr)
    }

    @Test
    fun testComposeInputField() = with(ctx) {
        val symbolicRef0 = mkRegisterReading(0, addressSort)
        val symbolicRef1 = mkRegisterReading(1, addressSort)
        val symbolicRef2 = mkRegisterReading(2, addressSort)
        val symbolicRef3 = mkRegisterReading(3, addressSort)

        val composedRef0 = mkConcreteHeapRef(-1)
        val composedRef1 = mkConcreteHeapRef(-2)
        val composedRef2 = mkConcreteHeapRef(-3)
        val composedRef3 = mkConcreteHeapRef(-4)

        val field = mockk<Field>()
        val inputField = UMemory1DArray(persistentMapOf(composedRef0 to composedRef0), concreteNull)
        val heapEvaluator = UHeapEagerModel<Field, Type>(
            concreteNull,
            mapOf(field to inputField),
            mapOf(),
            mapOf(),
            mapOf(),
            mapOf(),
        )

        val stackModel = URegistersStackEagerModel(
            concreteNull,
            mapOf(
                0 to composedRef0,
                1 to composedRef1,
                2 to composedRef2,
                3 to composedRef3
            )
        )

        val composer = UComposer(this, stackModel, heapEvaluator, mockk(), mockk())

        val region = emptyInputFieldCollection(field, addressSort)
            .write(symbolicRef1, symbolicRef1, trueExpr)
            .write(symbolicRef2, symbolicRef2, trueExpr)
            .write(symbolicRef3, symbolicRef3, trueExpr)
        val reading = region.read(symbolicRef0)

        val expr = composer.compose(reading)
        assertSame(composedRef0, expr)
    }
}