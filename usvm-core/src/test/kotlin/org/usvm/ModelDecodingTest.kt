package org.usvm

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
            UHeapModel<Field, Type>(
                mkConcreteHeapRef(NULL_ADDRESS),
                mockk(),
                persistentMapOf(),
                persistentMapOf(),
                persistentMapOf()
            )

        val stackModel = URegistersStackModel(mapOf(0 to ctx.mkBv(0), 1 to ctx.mkBv(0), 2 to ctx.mkBv(2)))

        val model = UModelBase(this, stackModel, heapModel, mockk(), mockk())


        val region = emptyAllocatedArrayRegion<Type, UBv32Sort>(mockk(), 1, bv32Sort)
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
        val concreteNull = mkConcreteHeapRef(NULL_ADDRESS)

        val heapModel =
            UHeapModel<Field, Type>(
                concreteNull,
                mockk(),
                persistentMapOf(),
                persistentMapOf(),
                persistentMapOf()
            )

        val stackModel = URegistersStackModel(mapOf(0 to mkBv(0), 1 to mkBv(0), 2 to mkBv(2)))

        val model = UModelBase(this, stackModel, heapModel, mockk(), mockk())


        val region = emptyAllocatedArrayRegion<Type, UAddressSort>(mockk(), 1, addressSort)
        val reading = region.read(mkRegisterReading(0, sizeSort))

        val expr = model.eval(reading)
        assertSame(concreteNull, expr)
    }
}
