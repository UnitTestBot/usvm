package org.usvm

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBv32Sort
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.api.writeArrayIndex
import org.usvm.api.writeArrayLength
import org.usvm.api.writeField
import org.usvm.collection.array.UAllocatedArrayId
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArrayId
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.USymbolicArrayIndex
import org.usvm.collection.array.USymbolicArrayIndexKeyInfo
import org.usvm.collection.array.USymbolicArrayInputToInputCopyAdapter
import org.usvm.collection.array.length.UInputArrayLengthId
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.field.UInputFieldId
import org.usvm.constraints.UTypeEvaluator
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.UMemory
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionUpdates
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.model.UModelBase
import org.usvm.model.URegistersStackEagerModel
import org.usvm.util.SetRegion
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class CompositionTest {
    private lateinit var stackEvaluator: UReadOnlyRegistersStack
    private lateinit var typeEvaluator: UTypeEvaluator<Type>
    private lateinit var mockEvaluator: UMockEvaluator
    private lateinit var memory: UReadOnlyMemory<Type>

    private lateinit var ctx: UContext
    private lateinit var concreteNull: UConcreteHeapRef
    private lateinit var composer: UComposer<Type>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()

        ctx = UContext(components)
        concreteNull = ctx.mkConcreteHeapRef(NULL_ADDRESS)
        stackEvaluator = mockk()
        typeEvaluator = mockk()
        mockEvaluator = mockk()

        memory = mockk()
        every { memory.types } returns typeEvaluator
        every { memory.stack } returns stackEvaluator
        every { memory.mocker } returns mockEvaluator

        composer = UComposer(ctx, memory)
    }

    @Test
    fun transformationWithoutEffect() = with(ctx) {
        val newExpr = object : UExpr<UBv32Sort>(ctx) {
            override val sort: UBv32Sort
                get() = bv32Sort

            override fun accept(transformer: KTransformerBase): UExpr<UBv32Sort> = this // do not transform

            override fun internEquals(other: Any): Boolean = structurallyEqual(other) { sort }

            override fun internHashCode(): Int = hash(sort)

            override fun print(printer: ExpressionPrinter) {
                printer.append("(test UExpr inheritor)")
            }
        }

        val resultValue = composer.compose(newExpr)

        assertSame(resultValue, newExpr)
    }

    @Test
    fun testNotOverriddenSymbolTransformer() {
        with(ctx) {
            val newExpr = object : USymbol<UBv32Sort>(ctx) {
                override val sort: UBv32Sort
                    get() = bv32Sort

                override fun accept(transformer: KTransformerBase): UExpr<UBv32Sort> {
                    // Call `transformer` to cause an exception
                    transformer.transform(this)
                    // Return `this` to get suitable types since return type of `transform` for
                    // KExpr<*> is Any to avoid creation of KExpr without overloading the transform function.
                    return this
                }

                override fun internEquals(other: Any): Boolean = structurallyEqual(other) { sort }

                override fun internHashCode(): Int = hash(sort)

                override fun print(printer: ExpressionPrinter) {
                    printer.append("(test USymbol inheritor)")
                }
            }

            assertThrows<IllegalStateException> { composer.compose(newExpr) }
        }
    }

    @Test
    fun testReplaceSimpleStackReading() = with(ctx) {
        val bv32Sort = mkBv32Sort()
        val idx = 5
        val expression = mkRegisterReading(idx, bv32Sort)
        val bvValue = 32.toBv()

        every { stackEvaluator.readRegister(idx, bv32Sort) } returns bvValue

        val composedExpression = composer.compose(expression) as UExpr<*>

        assertSame(composedExpression, bvValue)
    }

    @Test
    fun testReplacementOnSecondLevelOfExpression() = with(ctx) {
        val bv32Sort = mkBv32Sort()
        val idx = 5
        val readingValue = mkRegisterReading(idx, bv32Sort) as KExpr<KBv32Sort>
        val expression = mkBvNegationExpr(readingValue)
        val bvValue = 32.toBv()

        every { stackEvaluator.readRegister(idx, bv32Sort) } returns bvValue

        val composedExpression = composer.compose(expression) as UExpr<*>

        val simplifiedValue = (composedExpression as KBitVec32Value).intValue

        assertEquals(-32, simplifiedValue)
    }


    @Test
    fun testReplacementInTwoExpressions() = with(ctx) {
        val bv32Sort = mkBv32Sort()
        val idx = 5
        val readingValue = mkRegisterReading(idx, bv32Sort) as KExpr<KBv32Sort>
        val bvOneValue = 1.toBv()
        val bvZeroValue = 0.toBv()
        val bvValue = 32.toBv()

        val expression = mkOr(
            mkEq(mkBvNotExpr(readingValue), bvZeroValue),
            mkEq(readingValue, bvOneValue)
        )

        val expectedExpression = mkOr(
            mkEq(mkBvNotExpr(bvValue), bvZeroValue),
            mkEq(bvValue, bvOneValue)
        )

        every { stackEvaluator.readRegister(idx, bv32Sort) } returns bvValue

        val composedExpression = composer.compose(expression) as UExpr<*>

        assertSame(composedExpression, expectedExpression)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testUIndexedMethodReturnValue() = with(ctx) {
        val expression = mockk<UIndexedMethodReturnValue<*, *>>()
        val bvValue = 32.toBv()

        every { expression.accept(any()) } answers { (firstArg() as UComposer<*>).transform(expression) }
        every { mockEvaluator.eval(expression) } returns bvValue as UExpr<USort>

        val composedExpression = composer.compose(expression) as UExpr<*>

        assertSame(composedExpression, bvValue)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testUIsExprComposition() = with(ctx) {
        val typeResult = mkTrue()
        val addressFromMemory = 32.toBv()

        val heapRef = mockk<UHeapRef>(relaxed = true)
        val type = mockk<KClass<*>>(relaxed = true) // TODO replace with jacoDB type

        val isExpression = ctx.mkIsSubtypeExpr(heapRef, type)

        every { heapRef.accept(any()) } returns addressFromMemory as KExpr<UAddressSort>
        every { typeEvaluator.evalIsSubtype(addressFromMemory, type) } returns typeResult

        val composedExpression = composer.compose(isExpression)

        assertSame(composedExpression, mkTrue())
    }

    @Test
    fun testUArrayLength() = with(ctx) {
        val arrayType: KClass<Array<*>> = Array::class
        val firstAddress = 1
        val secondAddress = 2

        val fstAddress = mockk<UHeapRef>()
        val sndAddress = mockk<UHeapRef>()

        val fstResultValue = 1.toBv()
        val sndResultValue = 2.toBv()

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
            override fun mapKey(key: UHeapRef, composer: UComposer<*>?): UHeapRef =
                composer.compose(key)

            override fun eqSymbolic(ctx: UContext, key1: UHeapRef, key2: UHeapRef): UBoolExpr = key1 eq key2
        }

        val updates = UFlatUpdates<UHeapRef, USizeSort>(keyInfo)
            .write(fstAddress, fstResultValue, guard = trueExpr)
            .write(sndAddress, sndResultValue, guard = trueExpr)

        val collectionId = UInputArrayLengthId(arrayType, bv32Sort)
        val regionArray = USymbolicCollection(collectionId, updates)

        val fstConcreteAddress = mkConcreteHeapRef(firstAddress)
        val sndConcreteAddress = mkConcreteHeapRef(secondAddress)

        val firstReading = mkInputArrayLengthReading(regionArray, fstConcreteAddress)
        val secondReading = mkInputArrayLengthReading(regionArray, sndConcreteAddress)

        val fstValueFromHeap = 42.toBv()
        val sndValueFromHeap = 43.toBv()

        val heapToComposeWith = UMemory<KClass<Array<*>>, Any>(ctx, mockk())

        heapToComposeWith.writeArrayLength(fstConcreteAddress, fstValueFromHeap, arrayType)
        heapToComposeWith.writeArrayLength(sndConcreteAddress, sndValueFromHeap, arrayType)

        val composer = UComposer(ctx, heapToComposeWith)

        every { fstAddress.accept(composer) } returns fstConcreteAddress
        every { sndAddress.accept(composer) } returns sndConcreteAddress

        val fstComposedValue = composer.compose(firstReading)
        val sndComposedValue = composer.compose(secondReading)

        assertSame(fstResultValue, fstComposedValue)
        assertSame(sndResultValue, sndComposedValue)
    }

    @Test
    fun testUInputArrayIndexReading() = with(ctx) {
        val fstAddress = mockk<UHeapRef>()
        val sndAddress = mockk<UHeapRef>()
        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val keyEqualityComparer = { k1: USymbolicArrayIndex, k2: USymbolicArrayIndex ->
            mkAnd((k1.first == k2.first).expr, (k1.second == k2.second).expr)
        }

        val keyInfo = object : TestKeyInfo<USymbolicArrayIndex, SetRegion<USymbolicArrayIndex>> {
            override fun mapKey(key: USymbolicArrayIndex, composer: UComposer<*>?): USymbolicArrayIndex =
                composer.compose(key.first) to composer.compose(key.second)

            override fun cmpConcreteLe(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): Boolean = key1 == key2
            override fun eqSymbolic(ctx: UContext, key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): UBoolExpr =
                keyEqualityComparer(key1, key2)
        }

        val updates = UFlatUpdates<USymbolicArrayIndex, UBv32Sort>(keyInfo)
            .write(fstAddress to fstIndex, 42.toBv(), guard = trueExpr)
            .write(sndAddress to sndIndex, 43.toBv(), guard = trueExpr)

        val arrayType: KClass<Array<*>> = Array::class

        val region = USymbolicCollection(
            UInputArrayId(arrayType, bv32Sort),
            updates,
        )

        // TODO replace with jacoDB type
        val fstArrayIndexReading = mkInputArrayReading(region, fstAddress, fstIndex)
        // TODO replace with jacoDB type
        val sndArrayIndexReading = mkInputArrayReading(region, sndAddress, sndIndex)

        val answer = 43.toBv()

        val composer = UComposer(ctx, UMemory<KClass<*>, Any>(ctx, mockk())) // TODO replace with jacoDB type

        every { fstAddress.accept(composer) } returns sndAddress
        every { fstIndex.accept(composer) } returns sndIndex
        every { sndAddress.accept(composer) } returns sndAddress
        every { sndIndex.accept(composer) } returns sndIndex

        val fstComposedExpression = composer.compose(fstArrayIndexReading)
        val sndComposedExpression = composer.compose(sndArrayIndexReading)

        assertSame(fstComposedExpression, answer)
        assertSame(sndComposedExpression, answer)
    }

    @Test
    fun testComposeSeveralTimes() = with(ctx) {
        val fstAddress = mkRegisterReading(0, addressSort)
        val fstIndex = mkRegisterReading(1, sizeSort)

        val sndAddress = mkRegisterReading(2, addressSort)
        val sndIndex = mkRegisterReading(3, sizeSort)

        // TODO replace with jacoDB type
        val arrayType: KClass<Array<*>> = Array::class
        // Create an empty region
        val region = UInputArrayId(arrayType, mkBv32Sort()).emptyRegion()

        // create a reading from the region
        val fstArrayIndexReading = mkInputArrayReading(region, fstAddress, fstIndex)

        val sndMemory = UMemory<KClass<*>, Any>(ctx, mockk(), mockk())
        // create a heap with a record: (sndAddress, sndIndex) = 2
        sndMemory.writeArrayIndex(sndAddress, sndIndex, arrayType, mkBv32Sort(), 2.toBv(), mkTrue())

        val sndComposer = UComposer(ctx, sndMemory)

        val fstMemory = UMemory<KClass<*>, Any>(ctx, mockk(), mockk())
        // create a heap with a record: (fstAddress, fstIndex) = 1
        fstMemory.writeArrayIndex(fstAddress, fstIndex, arrayType, mkBv32Sort(), 1.toBv(), mkTrue())

        val fstComposer = UComposer(ctx, fstMemory) // TODO replace with jacoDB type

        // Both heaps leave everything untouched
        every { sndAddress.accept(sndComposer) } returns sndAddress
        every { sndAddress.accept(fstComposer) } returns sndAddress
        every { fstAddress.accept(sndComposer) } returns fstAddress
        every { fstAddress.accept(fstComposer) } returns fstAddress

        every { fstIndex.accept(fstComposer) } returns fstIndex
        every { fstIndex.accept(sndComposer) } returns fstIndex
        every { sndIndex.accept(fstComposer) } returns sndIndex
        every { sndIndex.accept(sndComposer) } returns sndIndex

        val sndComposedExpr = sndComposer.compose(fstArrayIndexReading)
        val fstComposedExpr = fstComposer.compose(sndComposedExpr)

        val expectedRegion = region
            .write(USymbolicArrayIndex(fstAddress, fstIndex), 1.toBv(), guard = mkTrue())
            .write(USymbolicArrayIndex(sndAddress, sndIndex), 2.toBv(), guard = mkTrue())

        require(fstComposedExpr is UInputArrayReading<*, *>)
        assert(fstComposedExpr.collection.updates.toList() == expectedRegion.updates.toList())
    }

    @Test
    fun testUAllocatedArrayIndexReading() = with(ctx) {
        val arrayType: KClass<Array<*>> = Array::class
        val address = 1

        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val fstSymbolicIndex = mockk<USizeExpr>()
        val sndSymbolicIndex = mockk<USizeExpr>()

        val keyInfo = object : TestKeyInfo<USizeExpr, SetRegion<USizeExpr>> {
            override fun mapKey(key: USizeExpr, composer: UComposer<*>?): USizeExpr = composer.compose(key)
            override fun eqSymbolic(ctx: UContext, key1: USizeExpr, key2: USizeExpr): UBoolExpr = key1 eq key2
        }

        val updates = UFlatUpdates<USizeExpr, UBv32Sort>(keyInfo)
            .write(fstIndex, 1.toBv(), guard = trueExpr)
            .write(sndIndex, 2.toBv(), guard = trueExpr)

        val collectionId = UAllocatedArrayId(arrayType, bv32Sort, address)
        val regionArray = USymbolicCollection(
            collectionId,
            updates,
        )

        val firstReading = mkAllocatedArrayReading(regionArray, fstSymbolicIndex)
        val secondReading = mkAllocatedArrayReading(regionArray, sndSymbolicIndex)

        val fstAddressForCompose = mkConcreteHeapRef(address)
        val sndAddressForCompose = mkConcreteHeapRef(address)

        val concreteIndex = mkBv(0)
        val fstValue = 42.toBv()
        val sndValue = 43.toBv()

        val heapToComposeWith = UMemory<KClass<Array<*>>, Any>(ctx, mockk())

        heapToComposeWith.writeArrayIndex(
            fstAddressForCompose, concreteIndex, arrayType, regionArray.sort, fstValue, guard = trueExpr
        )
        heapToComposeWith.writeArrayIndex(
            sndAddressForCompose, concreteIndex, arrayType, regionArray.sort, sndValue, guard = trueExpr
        )

        val composer = UComposer(ctx, heapToComposeWith)

        every { fstSymbolicIndex.accept(composer) } returns concreteIndex
        every { sndSymbolicIndex.accept(composer) } returns concreteIndex
        every { fstIndex.accept(composer) } returns concreteIndex
        every { sndIndex.accept(composer) } returns concreteIndex

        val fstComposedValue = composer.compose(firstReading)
        val sndComposedValue = composer.compose(secondReading)

        assertSame(2.toBv(), fstComposedValue)
        assertSame(fstComposedValue, sndComposedValue)
    }

    @Test
    fun testUAllocatedArrayAddressSortIndexReading() = with(ctx) {
        val arrayType: KClass<Array<*>> = Array::class

        val symbolicIndex = mockk<USizeExpr>()
        val symbolicAddress = mkRegisterReading(0, addressSort)

        val regionArray = UAllocatedArrayId(arrayType, addressSort, 0)
            .emptyRegion()
            .write(mkBv(0), symbolicAddress, trueExpr)
            .write(mkBv(1), mkConcreteHeapRef(1), trueExpr)

        val reading = mkAllocatedArrayReading(regionArray, symbolicIndex)

        val concreteNullRef = mkConcreteHeapRef(NULL_ADDRESS)

        val heapToComposeWith = UModelBase<KClass<Array<*>>>(
            ctx, mockk(), mockk(), mockk(), emptyMap(), concreteNullRef
        )

        val composer = spyk(UComposer(ctx, heapToComposeWith))

        every { symbolicIndex.accept(composer) } returns mkBv(2)
        every { symbolicAddress.accept(composer) } returns mkConcreteHeapRef(-1)

        val composedReading = composer.compose(reading)

        assertSame(composedReading, concreteNullRef)
    }

    @Test
    fun testUFieldReading() = with(ctx) {
        val aAddress = mockk<USymbolicHeapRef>()
        val bAddress = mockk<USymbolicHeapRef>()

        val keyInfo = object : TestKeyInfo<UHeapRef, SetRegion<UHeapRef>> {
            override fun mapKey(key: UHeapRef, composer: UComposer<*>?): UHeapRef = composer.compose(key)
            override fun eqSymbolic(ctx: UContext, key1: UHeapRef, key2: UHeapRef): UBoolExpr =
                (key1 == key2).expr
        }

        val updates = UFlatUpdates<UHeapRef, UBv32Sort>(keyInfo)
        val field = mockk<java.lang.reflect.Field>() // TODO replace with jacoDB field

        // An empty region with one write in it
        val region = USymbolicCollection(
            UInputFieldId(field, bv32Sort),
            updates,
        ).write(aAddress, 43.toBv(), guard = trueExpr)

        every { aAddress.accept(any()) } returns aAddress
        every { bAddress.accept(any()) } returns aAddress

        val expression = mkInputFieldReading(region, bAddress)

        /* Compose:
        fun foo(a: A, b: A) {
            b = a
            a.f = 42
            -------------------
            a.f = 43
            read b.f
         */

        val answer = 43.toBv()

        val composeMemory = UMemory<Type, Any>(ctx, mockk())
        composeMemory.writeField(aAddress, field, bv32Sort, 42.toBv(), guard = trueExpr)

        val composer = UComposer(ctx, composeMemory)

        val composedExpression = composer.compose(expression)

        assertSame(composedExpression, answer)
    }

    @Test
    fun testHeapRefEq() = with(ctx) {
        val concreteNull = ctx.mkConcreteHeapRef(NULL_ADDRESS)
        val stackModel = URegistersStackEagerModel(
            concreteNull, mapOf(0 to mkConcreteHeapRef(-1), 1 to mkConcreteHeapRef(-2))
        )
        val model = UModelBase<Type>(ctx, stackModel, mockk(), mockk(), emptyMap(), concreteNull)

        val composer = UComposer(this, model)

        val heapRefEvalEq = mkHeapRefEq(mkRegisterReading(0, addressSort), mkRegisterReading(1, addressSort))

        val expr = composer.compose(heapRefEvalEq)
        assertSame(falseExpr, expr)
    }

    @Test
    fun testHeapRefNullAddress() = with(ctx) {
        val stackModel = URegistersStackEagerModel(concreteNull, mapOf(0 to mkConcreteHeapRef(0)))

        val composedMemory: UReadOnlyMemory<Type> = mockk()
        every { composedMemory.nullRef() } returns concreteNull
        every { composedMemory.stack } returns stackModel

        val composer = UComposer(this, composedMemory)

        val heapRefEvalEq = mkHeapRefEq(mkRegisterReading(0, addressSort), nullRef)

        val expr = composer.compose(heapRefEvalEq)
        assertSame(trueExpr, expr)
    }

    @Test
    fun testComposeComplexRangedUpdate() = with(ctx) {
        val arrayType = mockk<Type>()

        val symbolicRef0 = mkRegisterReading(0, addressSort) as UHeapRef
        val symbolicRef1 = mkRegisterReading(1, addressSort) as UHeapRef
        val symbolicRef2 = mkRegisterReading(2, addressSort) as UHeapRef
        val composedSymbolicHeapRef = mkConcreteHeapRef(1)

        val composeMemory = UMemory<Type, Any>(ctx, mockk())

        composeMemory.writeArrayIndex(composedSymbolicHeapRef, mkBv(3), arrayType, bv32Sort, mkBv(1337), trueExpr)

        composeMemory.stack.push(4)
        composeMemory.stack.writeRegister(0, composedSymbolicHeapRef)
        composeMemory.stack.writeRegister(1, composedSymbolicHeapRef)
        composeMemory.stack.writeRegister(2, composedSymbolicHeapRef)
        composeMemory.stack.writeRegister(3, mkRegisterReading(3, bv32Sort))

        val composer = UComposer(ctx, composeMemory)

        val fromRegion0 = UInputArrayId(arrayType, bv32Sort)
            .emptyRegion()
            .write(symbolicRef0 to mkBv(0), mkBv(42), trueExpr)

        val adapter1 = USymbolicArrayInputToInputCopyAdapter(
            symbolicRef0 to mkSizeExpr(0),
            symbolicRef1 to mkSizeExpr(0),
            symbolicRef1 to mkSizeExpr(5),
            USymbolicArrayIndexKeyInfo
        )

        val fromRegion1 = fromRegion0
            .copyRange(fromRegion0, adapter1, trueExpr)

        val adapter2 = USymbolicArrayInputToInputCopyAdapter(
            symbolicRef1 to mkSizeExpr(0),
            symbolicRef2 to mkSizeExpr(0),
            symbolicRef2 to mkSizeExpr(5),
            USymbolicArrayIndexKeyInfo
        )

        val fromRegion2 = fromRegion1
            .copyRange(fromRegion1, adapter2, trueExpr)

        val idx0 = mkRegisterReading(3, bv32Sort)

        val reading0 = fromRegion2.read(symbolicRef2 to idx0)

        val composedExpr0 = composer.compose(reading0)
        val composedReading0 = assertIs<UAllocatedArrayReading<Type, UBv32Sort>>(composedExpr0)

        fun USymbolicCollectionUpdates<*, *>.allUpdates(): Collection<UUpdateNode<*, *>> =
            fold(mutableListOf()) { acc, r ->
                acc += r
                acc += (r as? URangedUpdateNode<*, *, *, *>)?.sourceCollection?.updates?.allUpdates() ?: emptyList()
                acc
            }

        val pinpointUpdates = composedReading0
            .collection
            .updates
            .allUpdates()
            .filterIsInstance<UPinpointUpdateNode<USizeExpr, UBv32Sort>>()

        assertTrue { pinpointUpdates.any { it.key == mkBv(3) && it.value == mkBv(1337) } }
        assertTrue { pinpointUpdates.any { it.key == mkBv(0) && it.value == mkBv(42) } }
    }

    @Test
    fun testNullRefRegionDefaultValue() = with(ctx) {
        val composedMemory: UReadOnlyMemory<Type> = mockk()
        every { composedMemory.nullRef() } returns concreteNull

        val stackModel = URegistersStackEagerModel(concreteNull, mapOf(0 to mkBv(0), 1 to mkBv(0), 2 to mkBv(2)))
        every { composedMemory.stack } returns stackModel

        val composer = UComposer(this, composedMemory)

        val region = UAllocatedArrayId(mockk<Type>(), addressSort, 1).emptyRegion()
        val reading = region.read(mkRegisterReading(0, sizeSort))

        val expr = composer.compose(reading)
        assertSame(concreteNull, expr)
    }

    @Test
    fun testUpdatesSimplification() = with(ctx) {
        val composedMemory: UReadOnlyMemory<Type> = mockk()

        val field = mockk<Field>()

        val ref0 = mkRegisterReading(0, addressSort)
        val ref1 = mkRegisterReading(1, addressSort)
        val ref2 = mkRegisterReading(2, addressSort)

        val region = UInputFieldId(field, bv32Sort)
            .emptyRegion()
            .write(ref0, mkBv(0), trueExpr)
            .write(ref1, mkBv(1), trueExpr)

        val reading = region.read(ref2)

        val composer = spyk(UComposer(this, composedMemory))

        val writableMemory: UWritableMemory<Type> = mockk()

        every { composer.transform(ref0) } returns mkConcreteHeapRef(-1)
        every { composer.transform(ref1) } returns ref1
        every { composer.transform(ref2) } returns ref2

        every { composedMemory.toWritableMemory() } returns writableMemory

        every {
            val lvalue = UFieldLValue(bv32Sort, ref1, field)
            writableMemory.write(lvalue, mkBv(1), trueExpr)
        } returns Unit
        every {
            val lvalue = UFieldLValue(bv32Sort, ref2, field)
            writableMemory.read(lvalue)
        } returns mkBv(42)

        val composedReading = composer.compose(reading)
        assertEquals(mkBv(42), composedReading)
        verify(exactly = 1) { writableMemory.write(any<UFieldLValue<Field, UBv32Sort>>(), any(), any())}
    }
}
