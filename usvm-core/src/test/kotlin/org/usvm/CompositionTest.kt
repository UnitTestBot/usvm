package org.usvm

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.ksmt.cache.hash
import org.ksmt.cache.structurallyEqual
import org.ksmt.expr.KBitVec32Value
import org.ksmt.expr.KExpr
import org.ksmt.expr.printer.ExpressionPrinter
import org.ksmt.expr.transformer.KTransformerBase
import org.ksmt.sort.KBv32Sort
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class CompositionTest<Type, Field> {
    private lateinit var stackEvaluator: URegistersStackEvaluator
    private lateinit var heapEvaluator: UReadOnlySymbolicHeap<Field, Type>
    private lateinit var typeEvaluator: UTypeEvaluator<Type>
    private lateinit var mockEvaluator: UMockEvaluator

    private lateinit var ctx: UContext
    private lateinit var composer: UComposer<Field, Type>

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
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

        assert(resultValue === newExpr)
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

        every { stackEvaluator.eval(idx, bv32Sort) } returns bvValue

        val composedExpression = composer.compose(expression) as UExpr<*>

        assert(composedExpression === bvValue)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testReplacementOnSecondLevelOfExpression() = with(ctx) {
        val bv32Sort = mkBv32Sort()
        val idx = 5
        val readingValue = mkRegisterReading(idx, bv32Sort) as KExpr<KBv32Sort>
        val expression = mkBvNegationExpr(readingValue)
        val bvValue = 32.toBv()

        every { stackEvaluator.eval(idx, bv32Sort) } returns bvValue

        val composedExpression = composer.compose(expression) as UExpr<*>

        val simplifiedValue = (composedExpression as KBitVec32Value).intValue

        assertEquals(-32, simplifiedValue)
    }


    @Suppress("UNCHECKED_CAST")
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

        every { stackEvaluator.eval(idx, bv32Sort) } returns bvValue

        val composedExpression = composer.compose(expression) as UExpr<*>

        assert(composedExpression === expectedExpression)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testUIndexedMethodReturnValue() = with(ctx) {
        val expression = mockk<UIndexedMethodReturnValue<*, *>>()
        val bvValue = 32.toBv()

        every { expression.accept(any()) } answers { (firstArg() as UComposer<*, *>).transform(expression) }
        every { mockEvaluator.eval(expression) } returns bvValue as UExpr<USort>

        val composedExpression = composer.compose(expression) as UExpr<*>

        assert(composedExpression === bvValue)
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

        assert(composedExpression === mkTrue())
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

        val updates = UEmptyUpdates<UHeapRef, USizeSort>(
            symbolicEq = { k1, k2 -> k1 eq k2 },
            concreteCmp = { _, _ -> throw UnsupportedOperationException() },
            symbolicCmp = { _, _ -> throw UnsupportedOperationException() }
        ).write(fstAddress, fstResultValue)
            .write(sndAddress, sndResultValue)

        val regionId = UInputArrayLengthId(arrayType)
        val regionArray = UInputArrayLengthMemoryRegion(
            regionId,
            bv32Sort,
            updates,
            defaultValue = sizeSort.defaultValue(),
            instantiator = { key, region -> mkInputArrayLength(region, key) }
        )

        val fstConcreteAddress = mkConcreteHeapRef(firstAddress)
        val sndConcreteAddress = mkConcreteHeapRef(secondAddress)

        val firstReading = mkInputArrayLength(regionArray, fstConcreteAddress)
        val secondReading = mkInputArrayLength(regionArray, sndConcreteAddress)

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
            mkAnd(k1.first eq k2.first, k1.second eq k2.second)
        }

        val initialNode = UPinpointUpdateNode(
            fstAddress to fstIndex,
            42.toBv(),
            keyEqualityComparer
        )

        val updates: UMemoryUpdates<USymbolicArrayIndex, UBv32Sort> = UFlatUpdates(
            initialNode,
            next = null,
            symbolicCmp = { _, _ -> shouldNotBeCalled() },
            concreteCmp = { k1, k2 -> k1 == k2 },
            symbolicEq = { k1, k2 -> keyEqualityComparer(k1, k2) }
        ).write(sndAddress to sndIndex, 43.toBv())

        val arrayType: KClass<Array<*>> = Array::class

        val region = UInputArrayMemoryRegion(
            UInputArrayId(arrayType),
            mkBv32Sort(),
            updates,
            defaultValue = null,
            instantiator = { key, memoryRegion -> mkInputArrayReading(memoryRegion, key.first, key.second) }
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

        assert(fstComposedExpression === answer)
        assert(sndComposedExpression === answer)
    }

    @Test
    fun testUAllocatedArrayIndexReading() = with(ctx) {
        val arrayType: KClass<Array<*>> = Array::class
        val address = 1

        val fstIndex = mockk<USizeExpr>()
        val sndIndex = mockk<USizeExpr>()

        val fstSymbolicIndex = mockk<USizeExpr>()
        val sndSymbolicIndex = mockk<USizeExpr>()

        val updates = UEmptyUpdates<USizeExpr, UBv32Sort>(
            symbolicEq = { k1, k2 -> k1 eq k2 },
            concreteCmp = { _, _ -> throw UnsupportedOperationException() },
            symbolicCmp = { _, _ -> throw UnsupportedOperationException() }
        ).write(fstIndex, 1.toBv())
            .write(sndIndex, 2.toBv())

        val regionId = UAllocatedArrayId(arrayType, address)
        val regionArray = UAllocatedArrayMemoryRegion(
            regionId,
            bv32Sort,
            updates,
            defaultValue = 0.toBv(),
            instantiator = { key, region -> mkAllocatedArrayReading(region, key) }
        )

        val firstReading = mkAllocatedArrayReading(regionArray, fstSymbolicIndex)
        val secondReading = mkAllocatedArrayReading(regionArray, sndSymbolicIndex)

        val fstAddressForCompose = mkConcreteHeapRef(address)
        val sndAddressForCompose = mkConcreteHeapRef(address)

        val concreteIndex = sizeSort.defaultValue()
        val fstValue = 42.toBv()
        val sndValue = 43.toBv()

        val heapToComposeWith = URegionHeap<Field, KClass<Array<*>>>(ctx)

        heapToComposeWith.writeArrayIndex(fstAddressForCompose, concreteIndex, arrayType, regionArray.sort, fstValue)
        heapToComposeWith.writeArrayIndex(sndAddressForCompose, concreteIndex, arrayType, regionArray.sort, sndValue)

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
    fun testUFieldReading() = with(ctx) {
        val aAddress = mockk<UHeapRef>()
        val bAddress = mockk<UHeapRef>()

        val updates = UEmptyUpdates<UHeapRef, UBv32Sort>(
            symbolicEq = { k1, k2 -> k1 eq k2 },
            concreteCmp = { _, _ -> throw UnsupportedOperationException() },
            symbolicCmp = { _, _ -> throw UnsupportedOperationException() }
        )
        val field = mockk<java.lang.reflect.Field>() // TODO replace with jacoDB field

        // An empty region with one write in it
        val region = UInputFieldMemoryRegion(
            UInputFieldRegionId(field),
            bv32Sort,
            updates,
            defaultValue = null,
            instantiator = { key, region -> mkInputFieldReading(region, key) }
        ).write(aAddress, 43.toBv())

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
        heapEvaluator.writeField(aAddress, field, bv32Sort, 42.toBv())

        // TODO replace with jacoDB type
        val composer = UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator)

        val composedExpression = composer.compose(expression)

        assert(composedExpression === answer)
    }
}
