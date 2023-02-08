package org.usvm

import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KClass
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.ksmt.cache.hash
import org.ksmt.cache.structurallyEqual
import org.ksmt.expr.KBvNotExpr
import org.ksmt.expr.KExpr
import org.ksmt.expr.printer.ExpressionPrinter
import org.ksmt.expr.transformer.KTransformerBase
import org.ksmt.sort.KBv32Sort

internal class CompositionTest<Type, Field> {
    private lateinit var stackEvaluator: UStackEvaluator
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
        val expression = mkBvNotExpr(readingValue)
        val bvValue = 32.toBv()

        every { stackEvaluator.eval(idx, bv32Sort) } returns bvValue

        val composedExpression = composer.compose(expression) as UExpr<*>

        val composedExpressionEquality = composedExpression === mkBvNotExpr(bvValue)
        val internalValueEquality = (composedExpression as KBvNotExpr<KBv32Sort>).value === bvValue

        assert(composedExpressionEquality && internalValueEquality)
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
        val region = mockk<UArrayLengthMemoryRegion>()
        val address = mockk<UHeapRef>()

        every { region.sort } returns bv32Sort

        val arrayType: KClass<Array<*>> = Array::class
        val arrayLength = ctx.mkArrayLength(region, address, arrayType) // TODO replace with jacoDB type
        val arrayBvLength = 32.toBv()

        val typeEvaluator = mockk<UTypeEvaluator<KClass<*>>>() // TODO replace with jacoDB type
        val heapEvaluator = mockk<UReadOnlySymbolicHeap<Field, KClass<*>>>() // TODO replace with jacoDB type
        val composer =
            UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator) // TODO replace with jacoDB type

        val addressFromMemory = object : UHeapRef(ctx) {
            override val sort: UAddressSort = addressSort

            override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> = this

            override fun internEquals(other: Any): Boolean = structurallyEqual(other) { sort }

            override fun internHashCode(): Int = hash(sort)

            override fun print(printer: ExpressionPrinter) {
                printer.append("Test arrayLength object")
            }
        }

        every { address.accept(any()) } returns addressFromMemory
        every { heapEvaluator.readArrayLength(addressFromMemory, arrayLength.arrayType) } returns arrayBvLength

        val composedExpression = composer.compose(arrayLength)

        assert(composedExpression === arrayBvLength)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testUInputArrayIndexReading() = with(ctx) {
        val region = mockk<UInputArrayMemoryRegion<UBv32Sort>>()

        every { region.sort } returns bv32Sort

        val address = mockk<UHeapRef>()
        val index = mockk<USizeExpr>()
        val arrayType: KClass<Array<*>> = Array::class

        val arrayIndexReading =
            mkInputArrayReading(region, address, index, arrayType, bv32Sort) // TODO replace with jacoDB type

        val arrayAddress = mkConcreteHeapRef(address = 12)
        val arrayIndex = mkBv(10)
        val answer = 42.toBv()

        val typeEvaluator = mockk<UTypeEvaluator<KClass<*>>>() // TODO replace with jacoDB type
        val heapEvaluator = mockk<UReadOnlySymbolicHeap<Field, KClass<*>>>() // TODO replace with jacoDB type
        val composer =
            UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator) // TODO replace with jacoDB type

        every { address.accept(any()) } returns arrayAddress
        every { index.accept(any()) } returns arrayIndex
        every { heapEvaluator.readArrayIndex(arrayAddress, arrayIndex, arrayType, bv32Sort) } returns answer

        val composedExpression = composer.compose(arrayIndexReading)

        assert(composedExpression === answer)
    }

    @Test
    fun testUAllocatedArrayIndexReading() = with(ctx) {
        val region = mockk<UAllocatedArrayMemoryRegion<UBv32Sort>>()

        every { region.sort } returns bv32Sort

        val address = 1
        val index = mockk<USizeExpr>()
        val arrayType: KClass<Array<*>> = Array::class

        val arrayIndexReading = mkAllocatedArrayReading(region, address, index, arrayType, bv32Sort)

        val composedIndex = 5.toBv()

        val typeEvaluator = mockk<UTypeEvaluator<KClass<*>>>() // TODO replace with jacoDB type
        val heapEvaluator = mockk<UReadOnlySymbolicHeap<Field, KClass<*>>>() // TODO replace with jacoDB type
        val composer =
            UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator) // TODO replace with jacoDB type

        val answer = 42.toBv()
        val heapRef = mkConcreteHeapRef(address)

        every { index.accept(composer) } returns composedIndex
        every { heapEvaluator.readArrayIndex(heapRef, composedIndex, arrayType, bv32Sort) } returns answer

        val composedExpression = composer.compose(arrayIndexReading)

        assert(composedExpression === answer)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun testUFieldReading() = with(ctx) {
        val region = mockk<UVectorMemoryRegion<*>>()
        val address = mockk<UHeapRef>()

        val fieldAddress = mkConcreteHeapRef(address = 12)

        every { region.sort } returns bv32Sort
        every { address.accept(any()) } returns fieldAddress

        val field = java.lang.reflect.Field::class // TODO replace with jacoco field
        val expression = mkFieldReading(region, address, field)

        val answer = 42.toBv()
        val heapEvaluator = mockk<UReadOnlySymbolicHeap<KClass<*>, Type>>() // TODO replace with jacoDB type
        val composer =
            UComposer(ctx, stackEvaluator, heapEvaluator, typeEvaluator, mockEvaluator) // TODO replace with jacoDB type

        every { heapEvaluator.readField(fieldAddress, field, bv32Sort) } returns answer

        val composedExpression = composer.compose(expression)

        assert(composedExpression === answer)
    }
}