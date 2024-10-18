package org.usvm.model

import io.mockk.every
import io.mockk.mockk
import org.usvm.collections.immutable.persistentHashMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.Field
import org.usvm.NULL_ADDRESS
import org.usvm.Type
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.collection.array.UAllocatedArrayId
import org.usvm.collection.array.UArrayEagerModelRegion
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.UInputArrayId
import org.usvm.collection.array.USymbolicArrayInputToAllocatedCopyAdapter
import org.usvm.collection.array.length.UArrayLengthEagerModelRegion
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collection.array.length.UInputArrayLengthId
import org.usvm.collection.field.UFieldsEagerModelRegion
import org.usvm.collection.field.UFieldsRegionId
import org.usvm.collection.field.UInputFieldId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.mkSizeExpr
import org.usvm.sampleUValue
import org.usvm.sizeSort
import kotlin.test.assertSame

class ModelCompositionTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var ownership: MutabilityOwnership
    private lateinit var concreteNull: UConcreteHeapRef

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        ownership = MutabilityOwnership()

        every { components.mkComposer(ctx) } answers { { memory: UReadOnlyMemory<Type>, ownership: MutabilityOwnership -> UComposer(ctx, memory, ownership) } }
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        concreteNull = ctx.mkConcreteHeapRef(NULL_ADDRESS)
    }

    @Test
    fun testComposeAllocatedArray() = with(ctx) {
        val stackModel = URegistersStackEagerModel(
            concreteNull,
            mapOf(0 to ctx.mkBv(0), 1 to ctx.mkBv(0), 2 to ctx.mkBv(2))
        )

        val model = UModelBase<Type>(ctx, stackModel, mockk(), mockk(), emptyMap(), concreteNull)
        val composer = UComposer(this, model, defaultOwnership)

        val region = UAllocatedArrayId<_, _, USizeSort>(mockk<Type>(), bv32Sort, 1)
            .emptyRegion()
            .write(0.toBv(), 0.toBv(), trueExpr, ownership)
            .write(1.toBv(), 1.toBv(), trueExpr, ownership)
            .write(mkRegisterReading(1, sizeSort), 2.toBv(), trueExpr, ownership)
            .write(mkRegisterReading(2, sizeSort), 3.toBv(), trueExpr, ownership)
        val reading = region.read(mkRegisterReading(0, sizeSort))

        val expr = composer.compose(reading)
        assertSame(mkBv(2), expr)
    }

    @Test
    fun testComposeRangedUpdate() = with(ctx) {
        val arrayType = mockk<Type>()
        val arrayMemoryId = UArrayRegionId<_, _, USizeSort>(arrayType, bv32Sort)

        val composedSymbolicHeapRef = ctx.mkConcreteHeapRef(-1)
        val inputArray = UMemory2DArray(
            persistentHashMapOf(ownership, (composedSymbolicHeapRef to mkBv(0)) to mkBv(1)), mkBv(0)
        )

        val arrayModel = UArrayEagerModelRegion(arrayMemoryId, inputArray)

        val stackModel = URegistersStackEagerModel(
            concreteNull, mapOf(0 to composedSymbolicHeapRef, 1 to mkBv(0))
        )

        val model = UModelBase<Type>(
            ctx, stackModel, mockk(), mockk(), mapOf(arrayMemoryId to arrayModel), concreteNull
        )
        val composer = UComposer(this, model, defaultOwnership)

        val symbolicRef = mkRegisterReading(0, addressSort) as UHeapRef

        val fromRegion = UInputArrayId<_, _, USizeSort>(arrayType, bv32Sort).emptyRegion()

        val concreteRef = mkConcreteHeapRef(1)

        val adapter = USymbolicArrayInputToAllocatedCopyAdapter(
            symbolicRef to mkSizeExpr(0),
            mkSizeExpr(0),
            mkSizeExpr(5),
            USizeExprKeyInfo()
        )

        val concreteRegion = UAllocatedArrayId<_, _, USizeSort>(arrayType, bv32Sort, concreteRef.address)
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
        val inputLength = UMemory1DArray(persistentHashMapOf(ownership, composedRef0 to mkBv(42)), mkBv(0))
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

        val composer = UComposer(this, model, defaultOwnership)

        val region = UInputArrayLengthId(arrayType, bv32Sort)
            .emptyRegion()
            .write(symbolicRef1, 0.toBv(), trueExpr, ownership)
            .write(symbolicRef2, 1.toBv(), trueExpr, ownership)
            .write(symbolicRef3, 2.toBv(), trueExpr, ownership)
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

        val inputField = UMemory1DArray(persistentHashMapOf(ownership, composedRef0 to composedRef0), concreteNull)
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

        val composer = UComposer(this, model, defaultOwnership)

        val region = UInputFieldId(field, addressSort)
            .emptyRegion()
            .write(symbolicRef1, symbolicRef1, trueExpr, ownership)
            .write(symbolicRef2, symbolicRef2, trueExpr, ownership)
            .write(symbolicRef3, symbolicRef3, trueExpr, ownership)
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

        val composer = UComposer(this, model, defaultOwnership)

        val emptyRegion = UAllocatedArrayId<_, _, USizeSort>(mockk<Type>(), bv32Sort, 1).emptyRegion()

        run {
            val region = emptyRegion
                .write(index0, nonDefaultValue0, trueGuard, ownership)
                .write(index0, nonDefaultValue1, falseGuard, ownership)
            val reading = region.read(index0)

            val expr = composer.compose(reading)
            assertEquals(nonDefaultValue0, expr)
        }

        run {
            val region = emptyRegion
                .write(index1, nonDefaultValue0, trueGuard, ownership)
                .write(index0, nonDefaultValue1, falseGuard, ownership)
            val reading = region.read(index0)

            val expr = composer.compose(reading)
            assertEquals(defaultValue, expr)
        }
    }
}
