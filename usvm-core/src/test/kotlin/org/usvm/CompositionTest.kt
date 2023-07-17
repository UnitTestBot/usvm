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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.usvm.constraints.UTypeEvaluator
import org.usvm.memory.UAllocatedArrayId
import org.usvm.memory.UAllocatedArrayRegion
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.UInputArrayId
import org.usvm.memory.UInputArrayLengthId
import org.usvm.memory.UInputArrayLengthRegion
import org.usvm.memory.UInputArrayRegion
import org.usvm.memory.UInputFieldId
import org.usvm.memory.UInputFieldRegion
import org.usvm.memory.UInputToInputKeyConverter
import org.usvm.memory.UMemoryUpdates
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlySymbolicHeap
import org.usvm.memory.URegionHeap
import org.usvm.memory.URegistersStackEvaluator
import org.usvm.memory.USymbolicArrayIndex
import org.usvm.memory.UUpdateNode
import org.usvm.memory.emptyAllocatedArrayRegion
import org.usvm.memory.emptyInputArrayRegion
import org.usvm.model.UHeapEagerModel
import org.usvm.model.URegistersStackEagerModel
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class CompositionTest {
    private lateinit var stackEvaluator: URegistersStackEvaluator
    private lateinit var heapEvaluator: UReadOnlySymbolicHeap<Field, Type>
    private lateinit var typeEvaluator: UTypeEvaluator<Type>
    private lateinit var mockEvaluator: UMockEvaluator

    private lateinit var ctx: UContext
    private lateinit var concreteNull: UConcreteHeapRef
    private lateinit var composer: UComposer<Field, Type>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()

        ctx = UContext(components)
        concreteNull = ctx.mkConcreteHeapRef(NULL_ADDRESS)
        stackEvaluator = mockk()
        heapEvaluator = mockk()
        typeEvaluator = mockk()
        mockEvaluator = mockk()

        composer = UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator)
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

        every { expression.accept(any()) } answers { (firstArg() as UComposer<*, *>).transform(expression) }
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
        val typeEvaluator = mockk<UTypeEvaluator<KClass<*>>>() // TODO replace with jacoDB type
        val heapEvaluator = mockk<UReadOnlySymbolicHeap<Field, KClass<*>>>() // TODO replace with jacoDB type
        val composer = UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator) // TODO remove

        val isExpression = ctx.mkIsExpr(heapRef, type)

        every { heapRef.accept(any()) } returns addressFromMemory as KExpr<UAddressSort>
        every { typeEvaluator.evalIs(addressFromMemory, type) } returns typeResult

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

        val updates = UFlatUpdates<UHeapRef, USizeSort>(
            symbolicEq = { k1, k2 -> k1 eq k2 },
            concreteCmp = { _, _ -> throw UnsupportedOperationException() },
            symbolicCmp = { _, _ -> throw UnsupportedOperationException() }
        ).write(fstAddress, fstResultValue, guard = trueExpr)
            .write(sndAddress, sndResultValue, guard = trueExpr)

        val regionId = UInputArrayLengthId(arrayType, bv32Sort, contextHeap = null)
        val regionArray = UInputArrayLengthRegion(
            regionId,
            updates,
        )

        val fstConcreteAddress = mkConcreteHeapRef(firstAddress)
        val sndConcreteAddress = mkConcreteHeapRef(secondAddress)

        val firstReading = mkInputArrayLengthReading(regionArray, fstConcreteAddress)
        val secondReading = mkInputArrayLengthReading(regionArray, sndConcreteAddress)

        val fstValueFromHeap = 42.toBv()
        val sndValueFromHeap = 43.toBv()

        val heapToComposeWith = URegionHeap<Field, KClass<Array<*>>>(ctx)

        heapToComposeWith.writeArrayLength(fstConcreteAddress, fstValueFromHeap, arrayType)
        heapToComposeWith.writeArrayLength(sndConcreteAddress, sndValueFromHeap, arrayType)

        val typeEvaluator = mockk<UTypeEvaluator<KClass<Array<*>>>>()
        val composer = UComposer(ctx, stackEvaluator, heapToComposeWith, typeEvaluator, mockEvaluator)

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

        val updates = UFlatUpdates<USymbolicArrayIndex, UBv32Sort>(
            symbolicCmp = { _, _ -> shouldNotBeCalled() },
            concreteCmp = { k1, k2 -> k1 == k2 },
            symbolicEq = { k1, k2 -> keyEqualityComparer(k1, k2) }
        ).write(fstAddress to fstIndex, 42.toBv(), guard = trueExpr)
            .write(sndAddress to sndIndex, 43.toBv(), guard = trueExpr)

        val arrayType: KClass<Array<*>> = Array::class

        val region = UInputArrayRegion(
            UInputArrayId(arrayType, bv32Sort, contextHeap = null),
            updates,
        )

        // TODO replace with jacoDB type
        val fstArrayIndexReading = mkInputArrayReading(region, fstAddress, fstIndex)
        // TODO replace with jacoDB type
        val sndArrayIndexReading = mkInputArrayReading(region, sndAddress, sndIndex)

        val answer = 43.toBv()

        val typeEvaluator = mockk<UTypeEvaluator<KClass<*>>>() // TODO replace with jacoDB type
        val heapEvaluator = URegionHeap<Field, KClass<*>>(ctx) // TODO replace with jacoDB type

        val composer = UComposer(
            ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator
        ) // TODO replace with jacoDB type

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

        val arrayType: KClass<Array<*>> = Array::class
        // Create an empty region
        val region = emptyInputArrayRegion(arrayType, mkBv32Sort())

        // TODO replace with jacoDB type
        // create a reading from the region
        val fstArrayIndexReading = mkInputArrayReading(region, fstAddress, fstIndex)

        val typeEvaluator = mockk<UTypeEvaluator<KClass<*>>>() // TODO replace with jacoDB type
        val sndHeapEvaluator = URegionHeap<Field, KClass<*>>(ctx) // TODO replace with jacoDB type
        // create a heap with a record: (sndAddress, sndIndex) = 2
        sndHeapEvaluator.writeArrayIndex(sndAddress, sndIndex, arrayType, mkBv32Sort(), 2.toBv(), mkTrue())

        val sndComposer = UComposer(
            ctx, stackEvaluator, sndHeapEvaluator, typeEvaluator, mockEvaluator
        ) // TODO replace with jacoDB type

        val fstEvaluator = URegionHeap<Field, KClass<*>>(ctx) // TODO replace with jacoDB type
        // create a heap with a record: (fstAddress, fstIndex) = 1
        fstEvaluator.writeArrayIndex(fstAddress, fstIndex, arrayType, mkBv32Sort(), 1.toBv(), mkTrue())

        val fstComposer = UComposer(
            ctx, stackEvaluator, fstEvaluator, typeEvaluator, mockEvaluator
        ) // TODO replace with jacoDB type

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
        assert(fstComposedExpr.region.updates.toList() == expectedRegion.updates.toList())
    }

    @Test
    fun testUAllocatedArrayIndexReading() = with(ctx) {
        val arrayType: KClass<Array<*>> = Array::class
        val address = 1

        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val fstSymbolicIndex = mockk<USizeExpr>()
        val sndSymbolicIndex = mockk<USizeExpr>()

        val updates = UFlatUpdates<USizeExpr, UBv32Sort>(
            symbolicEq = { k1, k2 -> k1 eq k2 },
            concreteCmp = { _, _ -> throw UnsupportedOperationException() },
            symbolicCmp = { _, _ -> throw UnsupportedOperationException() }
        ).write(fstIndex, 1.toBv(), guard = trueExpr)
            .write(sndIndex, 2.toBv(), guard = trueExpr)

        val regionId = UAllocatedArrayId(arrayType, bv32Sort, mkBv(0), address, contextHeap = null)
        val regionArray = UAllocatedArrayRegion(
            regionId,
            updates,
        )

        val firstReading = mkAllocatedArrayReading(regionArray, fstSymbolicIndex)
        val secondReading = mkAllocatedArrayReading(regionArray, sndSymbolicIndex)

        val fstAddressForCompose = mkConcreteHeapRef(address)
        val sndAddressForCompose = mkConcreteHeapRef(address)

        val concreteIndex = mkBv(0)
        val fstValue = 42.toBv()
        val sndValue = 43.toBv()

        val heapToComposeWith = URegionHeap<Field, KClass<Array<*>>>(ctx)

        heapToComposeWith.writeArrayIndex(
            fstAddressForCompose, concreteIndex, arrayType, regionArray.sort, fstValue, guard = trueExpr
        )
        heapToComposeWith.writeArrayIndex(
            sndAddressForCompose, concreteIndex, arrayType, regionArray.sort, sndValue, guard = trueExpr
        )

        val typeEvaluator = mockk<UTypeEvaluator<KClass<Array<*>>>>()
        val composer = UComposer(ctx, stackEvaluator, heapToComposeWith, typeEvaluator, mockEvaluator)

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

        val regionArray = emptyAllocatedArrayRegion(arrayType, 0, addressSort)
            .write(mkBv(0), symbolicAddress, trueExpr)
            .write(mkBv(1), mkConcreteHeapRef(1), trueExpr)

        val reading = mkAllocatedArrayReading(regionArray, symbolicIndex)

        val concreteNullRef = mkConcreteHeapRef(NULL_ADDRESS)
        val heapToComposeWith = UHeapEagerModel<Field, KClass<Array<*>>>(
            concreteNullRef,
            emptyMap(),
            emptyMap(),
            emptyMap()
        )

        val typeEvaluator = mockk<UTypeEvaluator<KClass<Array<*>>>>()
        val composer = spyk(UComposer(ctx, stackEvaluator, heapToComposeWith, typeEvaluator, mockEvaluator))

        every { symbolicIndex.accept(composer) } returns mkBv(2)
        every { symbolicAddress.accept(composer) } returns mkConcreteHeapRef(-1)

        val composedReading = composer.compose(reading)

        assertSame(composedReading, concreteNullRef)
    }

    @Test
    fun testUFieldReading() = with(ctx) {
        val aAddress = mockk<USymbolicHeapRef>()
        val bAddress = mockk<USymbolicHeapRef>()

        val updates = UFlatUpdates<UHeapRef, UBv32Sort>(
            symbolicEq = { k1, k2 -> (k1 == k2).expr },
            concreteCmp = { _, _ -> throw UnsupportedOperationException() },
            symbolicCmp = { _, _ -> throw UnsupportedOperationException() }
        )
        val field = mockk<java.lang.reflect.Field>() // TODO replace with jacoDB field

        // An empty region with one write in it
        val region = UInputFieldRegion(
            UInputFieldId(field, bv32Sort, contextHeap = null),
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
        // TODO replace with jacoDB type

        val heapEvaluator = URegionHeap<java.lang.reflect.Field, Type>(ctx)
        heapEvaluator.writeField(aAddress, field, bv32Sort, 42.toBv(), guard = trueExpr)

        // TODO replace with jacoDB type
        val composer = UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator)

        val composedExpression = composer.compose(expression)

        assertSame(composedExpression, answer)
    }

    @Test
    fun testHeapRefEq() = with(ctx) {
        val concreteNull = ctx.mkConcreteHeapRef(NULL_ADDRESS)
        val stackModel =
            URegistersStackEagerModel(concreteNull, mapOf(0 to mkConcreteHeapRef(-1), 1 to mkConcreteHeapRef(-2)))

        val composer = UComposer<Field, Type>(this, stackModel, mockk(), mockk(), mockk())

        val heapRefEvalEq = mkHeapRefEq(mkRegisterReading(0, addressSort), mkRegisterReading(1, addressSort))

        val expr = composer.compose(heapRefEvalEq)
        assertSame(falseExpr, expr)
    }

    @Test
    fun testHeapRefNullAddress() = with(ctx) {
        val stackModel = URegistersStackEagerModel(concreteNull, mapOf(0 to mkConcreteHeapRef(0)))

        val heapEvaluator: UReadOnlySymbolicHeap<Field, Type> = mockk()
        every { heapEvaluator.nullRef() } returns concreteNull

        val composer = UComposer(this, stackModel, heapEvaluator, mockk(), mockk())

        val heapRefEvalEq = mkHeapRefEq(mkRegisterReading(0, addressSort), nullRef)

        val expr = composer.compose(heapRefEvalEq)
        assertSame(trueExpr, expr)
    }

    @Test
    fun testComposeComplexRangedUpdate() = with(ctx) {
        val arrayType = mockk<Type>()

        val regionHeap = URegionHeap<Field, Type>(ctx)

        val symbolicRef0 = mkRegisterReading(0, addressSort)
        val symbolicRef1 = mkRegisterReading(1, addressSort)
        val symbolicRef2 = mkRegisterReading(2, addressSort)
        val composedSymbolicHeapRef = ctx.mkConcreteHeapRef(1)

        regionHeap.writeArrayIndex(composedSymbolicHeapRef, mkBv(3), arrayType, bv32Sort, mkBv(1337), trueExpr)

        val stackModel = URegistersStackEagerModel(
            concreteNull, mapOf(
                0 to composedSymbolicHeapRef,
                1 to composedSymbolicHeapRef,
                2 to composedSymbolicHeapRef,
                3 to ctx.mkRegisterReading(3, bv32Sort),
            )
        )
        val composer = UComposer(this, stackModel, regionHeap, mockk(), mockk())

        val fromRegion0 = emptyInputArrayRegion(arrayType, bv32Sort)
            .write(symbolicRef0 to mkBv(0), mkBv(42), trueExpr)

        val keyConverter1 = UInputToInputKeyConverter(symbolicRef0 to mkBv(0), symbolicRef1 to mkBv(0), mkBv(5))

        val fromRegion1 = fromRegion0
            .copyRange(fromRegion0, symbolicRef0 to mkBv(0), symbolicRef0 to mkBv(5), keyConverter1, trueExpr)

        val keyConverter2 = UInputToInputKeyConverter(symbolicRef1 to mkBv(0), symbolicRef2 to mkBv(0), mkBv(5))

        val fromRegion2 = fromRegion1
            .copyRange(fromRegion1, symbolicRef1 to mkBv(0), symbolicRef1 to mkBv(5), keyConverter2, trueExpr)

        val idx0 = mkRegisterReading(3, bv32Sort)

        val reading0 = fromRegion2.read(symbolicRef2 to idx0)

        val composedExpr0 = composer.compose(reading0)
        val composedReading0 = assertIs<UAllocatedArrayReading<Type, UBv32Sort>>(composedExpr0)

        fun UMemoryUpdates<*, *>.allUpdates(): Collection<UUpdateNode<*, *>> =
            fold(mutableListOf()) { acc, r ->
                acc += r
                acc += (r as? URangedUpdateNode<*, *, *, *>)?.region?.updates?.allUpdates() ?: emptyList()
                acc
            }

        val pinpointUpdates = composedReading0
            .region
            .updates
            .allUpdates()
            .filterIsInstance<UPinpointUpdateNode<USizeExpr, UBv32Sort>>()

        assertTrue { pinpointUpdates.any { it.key == mkBv(3) && it.value == mkBv(1337) } }
        assertTrue { pinpointUpdates.any { it.key == mkBv(0) && it.value == mkBv(42) } }
    }

    @Test
    fun testNullRefRegionDefaultValue() = with(ctx) {
        val heapEvaluator: UReadOnlySymbolicHeap<Field, Type> = mockk()

        every { heapEvaluator.nullRef() } returns concreteNull

        val stackModel = URegistersStackEagerModel(concreteNull, mapOf(0 to mkBv(0), 1 to mkBv(0), 2 to mkBv(2)))

        val composer = UComposer(this, stackModel, heapEvaluator, mockk(), mockk())

        val region = emptyAllocatedArrayRegion<Type, UAddressSort>(mockk(), 1, addressSort)
        val reading = region.read(mkRegisterReading(0, sizeSort))

        val expr = composer.compose(reading)
        assertSame(concreteNull, expr)
    }
}
