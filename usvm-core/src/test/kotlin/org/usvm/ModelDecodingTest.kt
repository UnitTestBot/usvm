package org.usvm

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ksmt.solver.z3.KZ3Solver
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf

class ModelDecodingTest {
    private lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
    }

    @Test
    fun testSmoke(): Unit = with(ctx) {
        val (translator, decoder) = buildDefaultTranslatorAndDecoder<Field, Type, Method>(ctx)
        val solver = USolverBase(this, KZ3Solver(this), translator, decoder)
        val status = solver.check(UMemoryBase(this, mockk()), UPathConstraintsSet(persistentSetOf(trueExpr)))
        assertIs<USolverSat<UModelBase<*, *>>>(status)
    }

}
