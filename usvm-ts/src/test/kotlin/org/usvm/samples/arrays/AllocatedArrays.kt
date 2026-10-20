package org.usvm.samples.arrays

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.test.util.checkers.eq as exactly
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.TsUnsupportedWitnessException
import org.usvm.util.assertNodeReplay
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import org.usvm.util.neq
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllocatedArrays : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/arrays/AllocatedArrays.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `test createConstantArrayOfNumbers`() {
        val method = getMethod("createConstantArrayOfNumbers")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r neq -1 }
            )
        )
    }

    @Test
    fun `test createAndReturnConstantArrayOfNumbers`() {
        val method = getMethod("createAndReturnConstantArrayOfNumbers")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            { r -> r.values.map { it.number } == listOf(1.0, 2.0, 3.0) },
        )
    }

    @Test
    fun `test createAndAccessArrayOfBooleans`() {
        val method = getMethod("createAndAccessArrayOfBooleans")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r neq -1 }
            )
        )
    }

    @Test
    fun `test createArrayOfBooleans`() {
        val method = getMethod("createArrayOfBooleans")
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsBoolean>>(
            method = method,
            { r -> r.values.map { it.value } == listOf(true, false, true) },
        )
    }

    @Test
    fun `test createMixedArray`() {
        val method = getMethod("createMixedArray")
        discoverProperties<TsTestValue.TsArray<*>>(
            method = method,
            { r ->
                if (r.values.size != 3) return@discoverProperties false
                val cond0 = r.values[0] is TsTestValue.TsNumber.TsDouble
                val cond1 = r.values[1] is TsTestValue.TsBoolean
                val cond2 = r.values[2] is TsTestValue.TsUndefined
                cond0 && cond1 && cond2
            },
        )
    }

    @Test
    fun `test createArrayOfUnknownValues`() {
        val method = getMethod("createArrayOfUnknownValues")
        discoverProperties<TsTestValue, TsTestValue, TsTestValue, TsTestValue.TsArray<*>>(
            method = method,
            { a, _, _, r -> (a as TsTestValue.TsNumber).number == 1.1 && r.values[0] == a },
            { _, b, _, r -> (b as TsTestValue.TsBoolean).value && r.values[1] == b },
            { _, _, c, r -> c is TsTestValue.TsUndefined && r.values[2] == c },
            invariants = arrayOf(
                { a, b, c, r -> r.values == listOf(a, b, c) }
            )
        )
    }

    @Test
    fun `test createArrayOfNumbersAndPutDifferentTypes`() {
        val method = getMethod("createArrayOfNumbersAndPutDifferentTypes")
        val expected = listOf(TsTestValue.TsNull, TsTestValue.TsBoolean(value = true), TsTestValue.TsUndefined)

        checkMatches<TsTestValue.TsArray<*>>(
            method = method,
            analysisResultNumberMatches = exactly(1),
            { result -> result.values == expected },
        )
    }

    @Test
    fun `test allocatedArrayLengthExpansion`() {
        val target = getMethod(methodName = "allocatedArrayLengthExpansion")
        val result = TsMachine(
            scene = scene,
            options = UMachineOptions(solverType = SolverType.YICES),
            tsOptions = TsOptions(),
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(target)) }

        assertEquals(expected = 1, actual = result.states.size)
        assertFalse(result.runtimeLimited)
        assertFalse(result.engineFailed)
        val witnessFailure = assertFailsWith<TsUnsupportedWitnessException> {
            TsTestResolver().resolve(target, result.states.single())
        }
        assertTrue(witnessFailure.message.orEmpty().contains("Sparse array slot 3"))

        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod(methodName = "inspectAllocatedArrayLengthExpansion"),
            { value -> value.number == 5.0 },
            invariants = arrayOf({ value -> value.number == 5.0 }),
        )
    }

    @Test
    fun `test writeInTheIndexEqualToLength`() {
        val method = getMethod(methodName = "writeInTheIndexEqualToLength")
        val expected = listOf(1.0, 2.0, 3.0, 4.0)

        checkMatches<TsTestValue.TsArray<TsTestValue.TsNumber>>(
            method = method,
            analysisResultNumberMatches = exactly(1),
            { result -> result.values.map { it.number } == expected },
        )
    }

    @Test
    fun `write at the allocated array length appends in native JavaScript`() {
        val source = getResourcePath(tsPath).readText()
        val script = source + """

            const values = new AllocatedArrays().writeInTheIndexEqualToLength();
            const expected = [1, 2, 3, 4];
            if (values.length !== expected.length || values.some((value, index) => value !== expected[index])) {
                throw new Error('Appending at the allocated array length changed its contents');
            }
        """.trimIndent()

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "allocated-array-append",
            timeoutMessage = "Allocated array append replay timed out",
        )
    }

    @Test
    fun `sparse allocated growth preserves values length and holes in native JavaScript`() {
        val script = getResourcePath(tsPath).readText() + "\n" +
            getResourcePath("/models/NumericBoundarySemantics.ts").readText() + """

            const subject = new AllocatedArrays();
            const values = subject.allocatedArrayLengthExpansion();
            if (!Array.isArray(values) || values.length !== 6) throw new Error('Sparse length');
            if (values[0] !== 1 || values[1] !== 2 || values[2] !== 3 || values[5] !== 5) throw new Error('Sparse values');
            if (3 in values || 4 in values) throw new Error('Sparse holes became own elements');
            if (values[3] !== undefined || values[4] !== undefined) throw new Error('Sparse hole read');
            if (subject.inspectAllocatedArrayLengthExpansion() !== 5) throw new Error('Sparse observation');
            if (new NumericBoundarySemantics().outOfRangeArrayWrite() !== 22) throw new Error('Existing element changed');
        """.trimIndent()

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "allocated-array-sparse-growth",
            timeoutMessage = "Allocated sparse growth native replay timed out",
        )
    }
}
