package org.usvm

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ksmt.utils.mkConst
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS
import kotlin.test.assertSame
import kotlinx.collections.immutable.persistentMapOf

class ModelDecodingTest {
    private lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
    }

    @Test
    fun testVeryTrickyCase() = with(ctx) {
        val heapModel =
            UHeapModel<Field, ArrayType>(mkConcreteHeapRef(0), mockk(), persistentMapOf(), persistentMapOf(), persistentMapOf())

        val stackModel = URegistersStackModel(mapOf(0 to ctx.mkBv(0), 1 to ctx.mkBv(0), 2 to ctx.mkBv(2)))

        val model = UModelBase(this, stackModel, heapModel, mockk(), mockk())


        val region = emptyAllocatedArrayRegion(mockk(), 1, bv32Sort) { key, reg ->
            ctx.mkAllocatedArrayReading(reg, key)
        }
            .write(0.toBv(), 0.toBv(), trueExpr)
            .write(1.toBv(), 1.toBv(), trueExpr)
            .write(mkRegisterReading(1, sizeSort), 2.toBv(), trueExpr)
            .write(mkRegisterReading(2, sizeSort), 3.toBv(), trueExpr)
        val reading = region.read(mkRegisterReading(0, sizeSort))

         val expr = model.eval(reading)
        assertSame(mkBv(2), expr)
    }

    @Test
    fun testTrickyCase() = with(ctx) {
        val heapModel =
            UHeapModel<Field, ArrayType>(mkConcreteHeapRef(0), mockk(), persistentMapOf(), persistentMapOf(), persistentMapOf())

        val stackModel = URegistersStackModel(mapOf(0 to mkBv(0), 1 to mkBv(0), 2 to mkBv(2)))

        val model = UModelBase(this, stackModel, heapModel, mockk(), mockk())


        val region = emptyAllocatedArrayRegion(mockk(), 1, addressSort) { key, reg ->
            mkAllocatedArrayReading(reg, key)
        }
        val reading = region.read(mkRegisterReading(0, sizeSort))

        val expr = model.eval(reading)
        assertSame(mkConcreteHeapRef(NULL_ADDRESS), expr)
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun testHeapRefEq() = with(ctx) {
        val translator = UExprTranslator<Field, ArrayType>(this)

        val heapModel =
            UHeapModel<Field, ArrayType>(mkConcreteHeapRef(0), mockk(), persistentMapOf(), persistentMapOf(), persistentMapOf())

        val stackModel = URegistersStackModel(mapOf(0 to mkConcreteHeapRef(-1), 1 to mkConcreteHeapRef(-2)))

        val model = UModelBase(this, stackModel, heapModel, mockk(), mockk())

        val heapRefEvalEq = mkHeapRefEq(mkRegisterReading(0, addressSort), mkRegisterReading(1, addressSort))

        val expr = model.eval(heapRefEvalEq)
        assertSame(falseExpr, expr)
    }
}
