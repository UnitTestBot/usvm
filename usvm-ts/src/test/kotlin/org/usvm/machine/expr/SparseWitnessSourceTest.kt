package org.usvm.machine.expr

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.io.TempDir
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsUnsupportedWitnessException
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SparseWitnessSourceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/SparseWitnessSource.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))

    @Test
    fun `deleted slot refuses a concrete witness instead of exposing its stale payload`() {
        val failure = assertFailsWith<TsUnsupportedWitnessException> {
            discoverProperties<TsTestValue.TsArray<*>>(
                method = getMethod(methodName = "sparseResult", className = "SparseWitnessSource"),
                { result -> result.values.size == 2 },
            )
        }

        assertTrue(failure.message.orEmpty().contains("Sparse array slot 0"))
    }

    @Test
    fun `own slot containing undefined remains representable`() {
        discoverProperties<TsTestValue.TsArray<*>>(
            method = getMethod(methodName = "explicitUndefined", className = "SparseWitnessSource"),
            { result -> result.values.first() == TsTestValue.TsUndefined },
            invariants = arrayOf({ result -> result.values.size == 2 }),
        )
    }

    @Test
    fun `scalar computation over a hole remains supported`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "scalarResult", className = "SparseWitnessSource"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `native execution distinguishes holes from explicit undefined`() {
        val script = source.readText() + """

            const sparse = SparseWitnessSource.sparseResult();
            const explicit = SparseWitnessSource.explicitUndefined();
            if (0 in sparse || !(1 in sparse) || sparse[0] !== undefined) throw Error('sparse slot');
            if (!(0 in explicit) || explicit[0] !== undefined) throw Error('explicit undefined');
            if (SparseWitnessSource.scalarResult() !== 1) throw Error('scalar result');
        """.trimIndent()

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "sparse-witness",
            timeoutMessage = "Sparse witness source replay timed out",
        )
    }
}
