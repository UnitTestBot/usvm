package org.usvm.model

import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.Field
import org.usvm.NULL_ADDRESS
import org.usvm.Type
import org.usvm.UComponents
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.memory.collection.adapter.USymbolicArrayCopyAdapter
import org.usvm.memory.collection.adapter.USymbolicArrayInputToAllocatedCopyAdapter
import org.usvm.memory.collection.id.UAllocatedArrayId
import org.usvm.memory.collection.id.UInputArrayId
import org.usvm.memory.collection.id.UInputArrayLengthId
import org.usvm.memory.collection.id.UInputFieldId
import org.usvm.memory.collection.key.USizeExprKeyInfo
import org.usvm.memory.collection.region.UArrayLengthsRegionId
import org.usvm.memory.collection.region.UArrayRegionId
import org.usvm.memory.collection.region.UFieldsRegionId
import org.usvm.model.region.UArrayEagerModelRegion
import org.usvm.model.region.UArrayLengthEagerModelRegion
import org.usvm.model.region.UFieldsEagerModelRegion
import org.usvm.sampleUValue
import kotlin.test.assertSame

class ModelCompositionTest {
    private lateinit var ctx: UContext
    private lateinit var concreteNull: UConcreteHeapRef

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        concreteNull = ctx.mkConcreteHeapRef(NULL_ADDRESS)
    }

    @Test
    fun testComposeAllocatedArray() = with(ctx) {
        val stackModel = URegistersStackEagerModel(
            concreteNull,
            mapOf(0 to ctx.mkBv(0), 1 to ctx.mkBv(0), 2 to ctx.mkBv(2))
        )

        val model = UModelBase<Type>(ctx, stackModel, mockk(), mockk(), emptyMap(), concreteNull)
        val composer = UComposer(this, model)

        val region = UAllocatedArrayId(mockk<Type>(), bv32Sort, mkBv(0), 1)
            .emptyRegion()
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
        val arrayMemoryId = UArrayRegionId(arrayType, bv32Sort)

        val composedSymbolicHeapRef = ctx.mkConcreteHeapRef(-1)
        val inputArray = UMemory2DArray(
            persistentMapOf((composedSymbolicHeapRef to mkBv(0)) to mkBv(1)), mkBv(0)
        )
        val arrayModel = UArrayEagerModelRegion(arrayMemoryId, emptyMap(), inputArray)

        val stackModel = URegistersStackEagerModel(
            concreteNull, mapOf(0 to composedSymbolicHeapRef, 1 to mkBv(0))
        )

        val model = UModelBase<Type>(
            ctx, stackModel, mockk(), mockk(), mapOf(arrayMemoryId to arrayModel), concreteNull
        )
        val composer = UComposer(this, model)

        val symbolicRef = mkRegisterReading(0, addressSort) as UHeapRef

        val fromRegion = UInputArrayId(arrayType, bv32Sort).emptyRegion()

        val concreteRef = mkConcreteHeapRef(1)

        val adapter = USymbolicArrayInputToAllocatedCopyAdapter(
            symbolicRef to mkSizeExpr(0),
            mkSizeExpr(0),
            mkSizeExpr(5),
            USizeExprKeyInfo
        )

        val concreteRegion = UAllocatedArrayId(arrayType, bv32Sort, mkBv(0), concreteRef.address)
            .emptyRegion()
            .copyRange(fromRegion, adapter, trueExpr)

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

        val arrayLengthMemoryId = UArrayLengthsRegionId(sizeSort, arrayType)
        val inputLength = UMemory1DArray(persistentMapOf(composedRef0 to mkBv(42)), mkBv(0))
        val arrayLengthModel = UArrayLengthEagerModelRegion(arrayLengthMemoryId, inputLength)

        val stackModel = URegistersStackEagerModel(
            concreteNull,
            mapOf(
                0 to composedRef0,
                1 to composedRef1,
                2 to composedRef2,
                3 to composedRef3
            )
        )

        val model = UModelBase<Type>(
            ctx, stackModel, mockk(), mockk(), mapOf(arrayLengthMemoryId to arrayLengthModel), concreteNull
        )

        val composer = UComposer(this, model)

        val region = UInputArrayLengthId(arrayType, bv32Sort)
            .emptyRegion()
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
        val fieldMemoryId = UFieldsRegionId(field, addressSort)

        val inputField = UMemory1DArray(persistentMapOf(composedRef0 to composedRef0), concreteNull)
        val fieldModel = UFieldsEagerModelRegion(fieldMemoryId, inputField)

        val stackModel = URegistersStackEagerModel(
            concreteNull,
            mapOf(
                0 to composedRef0,
                1 to composedRef1,
                2 to composedRef2,
                3 to composedRef3
            )
        )

        val model = UModelBase<Type>(
            ctx, stackModel, mockk(), mockk(), mapOf(fieldMemoryId to fieldModel), concreteNull
        )

        val composer = UComposer(this, model)

        val region = UInputFieldId(field, addressSort)
            .emptyRegion()
            .write(symbolicRef1, symbolicRef1, trueExpr)
            .write(symbolicRef2, symbolicRef2, trueExpr)
            .write(symbolicRef3, symbolicRef3, trueExpr)
        val reading = region.read(symbolicRef0)

        val expr = composer.compose(reading)
        assertSame(composedRef0, expr)
    }

    @Test
    fun testComposeAllocatedArrayWithFalseOverwrite() = with(ctx) {
        val index0 = 0.toBv()
        val index1 = 1.toBv()

        val defaultValue = bv32Sort.sampleUValue()
        val nonDefaultValue0 = 17.toBv()
        val nonDefaultValue1 = 42.toBv()

        val stackModel = URegistersStackEagerModel(concreteNull, mapOf(0 to trueExpr, 1 to falseExpr))
        val trueGuard = mkRegisterReading(0, boolSort)
        val falseGuard = mkRegisterReading(1, boolSort)

        val model = UModelBase<Type>(
            ctx, stackModel, mockk(), mockk(), emptyMap(), concreteNull
        )

        val composer = UComposer(this, model)

        val emptyRegion = UAllocatedArrayId(mockk<Type>(), bv32Sort, mkBv(0), 1).emptyRegion()

        run {
            val region = emptyRegion
                .write(index0, nonDefaultValue0, trueGuard)
                .write(index0, nonDefaultValue1, falseGuard)
            val reading = region.read(index0)

            val expr = composer.compose(reading)
            assertEquals(nonDefaultValue0, expr)
        }

        run {
            val region = emptyRegion
                .write(index1, nonDefaultValue0, trueGuard)
                .write(index0, nonDefaultValue1, falseGuard)
            val reading = region.read(index0)

            val expr = composer.compose(reading)
            assertEquals(defaultValue, expr)
        }
    }
}
