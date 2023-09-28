package org.usvm.solver

import io.ksmt.expr.KBitVec32Value
import io.ksmt.solver.z3.KZ3Solver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeSort
import org.usvm.constraints.UPathConstraints
import org.usvm.collection.array.length.UInputArrayLengthId
import org.usvm.model.ULazyModelDecoder
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.sizeSort
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.assertSame

private typealias Type = SingleTypeSystem.SingleType

open class SoftConstraintsTest<Field> {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var softConstraintsProvider: USoftConstraintsProvider<Type, *>
    private lateinit var translator: UExprTranslator<Type, *>
    private lateinit var decoder: ULazyModelDecoder<Type>
    private lateinit var solver: USolverBase<Type, UContext<*>>

    @BeforeEach
    fun initialize() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns SingleTypeSystem

        ctx = UContext(components)
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        softConstraintsProvider = USoftConstraintsProvider(ctx)

        val translatorWithDecoder = buildTranslatorAndLazyDecoder<Type, USizeSort>(ctx)

        translator = translatorWithDecoder.first
        decoder = translatorWithDecoder.second
        val typeSolver = UTypeSolver(SingleTypeSystem)
        solver = USolverBase(ctx, KZ3Solver(ctx), typeSolver, translator, decoder, softConstraintsProvider)
    }

    @Test
    fun testItWorks() = with(ctx) {
        val fstRegister = mkRegisterReading(idx = 0, bv32Sort)
        val sndRegister = mkRegisterReading(idx = 1, bv32Sort)
        val expr = mkBvSignedLessOrEqualExpr(fstRegister, sndRegister)

        val pc = UPathConstraints<Type, UContext<*>>(ctx)
        pc += expr

        val result = solver.checkWithSoftConstraints(pc) as USatResult
        val model = result.model

        val fstRegisterValue = model.eval(fstRegister)
        val sndRegisterValue = model.eval(sndRegister)

        assertSame(fstRegisterValue, sndRegisterValue)
    }

    @Test
    @Disabled("How to count number of calls correctly?")
    fun expressionsCalculatesSoftConstraintsOnlyOnce() = with(ctx) {
        val fstRegister = mkRegisterReading(idx = 0, bv32Sort)
        val sndRegister = mkRegisterReading(idx = 1, bv32Sort)
        val thirdRegister = mkRegisterReading(idx = 2, bv32Sort)

        val fstExpr = mkBvSignedLessOrEqualExpr(fstRegister, sndRegister)
        val sndExpr = mkBvSignedLessOrEqualExpr(sndRegister, thirdRegister)
        val sameAsFirstExpr = mkBvSignedLessOrEqualExpr(fstRegister, sndRegister)

        val softConstraintsProvider = mockk<USoftConstraintsProvider<Type, *>>()

        every { softConstraintsProvider.provide(any()) } answers { callOriginal() }

        val pc = UPathConstraints<Type, UContext<*>>(ctx)
        pc += fstExpr
        pc += sndExpr
        pc += sameAsFirstExpr

        val typeSolver = UTypeSolver<Type>(mockk())
        val solver: USolverBase<Type, UContext<*>> =
            USolverBase(ctx, KZ3Solver(ctx), typeSolver, translator, decoder, softConstraintsProvider)

        val result = solver.checkWithSoftConstraints(pc) as USatResult
        val model = result.model

        verify(exactly = 1) {
            softConstraintsProvider.transform(fstRegister)
            softConstraintsProvider.transform(sndRegister)
            softConstraintsProvider.transform(thirdRegister)
            softConstraintsProvider.transform(fstExpr)
            softConstraintsProvider.transform(sndExpr)
            softConstraintsProvider.transform(sameAsFirstExpr)

            softConstraintsProvider.provide(sndExpr)
        }

        verify(exactly = 2) {
            softConstraintsProvider.provide(fstExpr)
        }

        val fstEvaluated = model.eval(fstRegister)
        val sndEvaluated = model.eval(sndRegister)
        val thirdEvaluated = model.eval(thirdRegister)

        assertSame(fstEvaluated, sndEvaluated)
        assertSame(fstEvaluated, thirdEvaluated)
    }

    @Test
    fun softConflictingWithPathConstraints() = with(ctx) {
        val arrayType = IntArray::class
        val inputRef = mkRegisterReading(0, addressSort)
        val secondInputRef = mkRegisterReading(1, addressSort)
        val region = UInputArrayLengthId(arrayType, sizeSort)
            .emptyRegion()
            .write(inputRef, mkRegisterReading(3, sizeSort), guard = trueExpr)

        val size = 25

        val reading = region.read(secondInputRef)

        val pc = UPathConstraints<Type, UContext<*>>(ctx)
        pc += reading eq size.toBv()
        pc += inputRef eq secondInputRef
        pc += (inputRef eq nullRef).not()

        val result = (solver.checkWithSoftConstraints(pc)) as USatResult

        val model = result.model
        val value = model.eval(mkInputArrayLengthReading(region, inputRef))

        assertSame(size.toBv(), value)
    }

    @Test
    fun testUnsatCore() = with(ctx) {
        val arrayType = IntArray::class
        val inputRef = mkRegisterReading(0, addressSort)
        val region = UInputArrayLengthId(arrayType, sizeSort)
            .emptyRegion()
            .write(inputRef, mkRegisterReading(3, sizeSort), guard = trueExpr)

        val pc = UPathConstraints<Type, UContext<*>>(ctx)
        pc += (inputRef eq nullRef).not()
        val result = (solver.checkWithSoftConstraints(pc)) as USatResult

        val model = result.model
        val value = model.eval(mkInputArrayLengthReading(region, inputRef))

        assert((value as KBitVec32Value).intValue < 10)
    }

    @Test
    fun testSimpleComparisonExpression(): Unit = with(ctx) {
        val inputRef = mkRegisterReading(0, bv32Sort)
        val bvValue = 0.toBv()
        val expression = mkBvSignedLessOrEqualExpr(bvValue, inputRef).not()

        val pc = UPathConstraints<Type, UContext<*>>(ctx)
        pc += expression
        val result = (solver.checkWithSoftConstraints(pc)) as USatResult

        val model = result.model
        model.eval(expression)
    }
}
