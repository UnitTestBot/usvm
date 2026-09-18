package org.usvm.machine.call

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsArrayShiftReplayTest {
    @TempDir
    lateinit var directory: Path

    @TestFactory
    fun `symbolic shift inputs results and heap changes replay in JavaScript`(): List<DynamicTest> {
        val cases = replayCases()
        val source = directory.resolve("ArrayShiftReplay.ts")
        source.writeText(renderSource(cases))
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val methods = scene.projectClasses.single { it.name == "ArrayShiftReplay" }.methods.associateBy { it.name }

        return cases.mapIndexed { index, case ->
            DynamicTest.dynamicTest(case.name) {
                val method = methods.getValue("case$index")

                val tests = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
                    machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
                }

                assertTrue(tests.isNotEmpty())
                val results = tests.map {
                    assertIs<TsTestValue.TsNumber>(it.returnValue, message = it.toString()).number
                }.toSet()
                assertEquals((0..case.maxResult).map { it.toDouble() }.toSet(), results)

                val script = buildString {
                    appendLine(renderSource(cases))
                    appendLine(JS_SAME_VALUE)
                    tests.forEachIndexed { stateIndex, test ->
                        val arguments = test.before.parameters.joinToString(transform = ::jsValue)
                        val expectedAfter = test.after.parameters.joinToString(transform = ::jsValue)
                        appendLine("{")
                        appendLine("const args = [$arguments];")
                        appendLine("const actual = new ArrayShiftReplay().case$index(...args);")
                        val expectedResult = jsValue(test.returnValue)
                        appendLine("if (!same(actual, $expectedResult)) throw Error('result $stateIndex');")
                        appendLine("if (!same(args, [$expectedAfter])) throw Error('heap $stateIndex');")
                        appendLine("}")
                    }
                }
                assertReplay(script, index)
            }
        }
    }

    private fun replayCases(): List<ReplayCase> = buildList {
        for (type in listOf("any", "unknown")) {
            add(
                ReplayCase(
                    name = "first $type read",
                    parameters = "values: $type[]",
                    maxResult = 6,
                    body = """
                        if (values.length !== 1) return 0;
                        const removed = values.shift();
                        if (removed === 42) return 1;
                        if (removed === true) return 2;
                        if (removed === false) return 3;
                        if (removed === null) return 4;
                        if (removed === undefined) return 5;
                        return 6;
                    """.trimIndent(),
                )
            )
            add(
                ReplayCase(
                    name = "repeated mixed $type shifts",
                    parameters = "values: $type[]",
                    maxResult = 3,
                    body = """
                        if (values.length !== 2) return 0;
                        const first = values.shift();
                        const second = values.shift();
                        if (first === 42 && second === true) return 1;
                        if (first === false && second === 17) return 2;
                        return 3;
                    """.trimIndent(),
                )
            )
            add(
                ReplayCase(
                    name = "read shifted $type tail",
                    parameters = "values: $type[]",
                    maxResult = 3,
                    body = """
                        if (values.length !== 2) return 0;
                        values.shift();
                        if (values[0] === 42) return 1;
                        if (values[0] === true) return 2;
                        return 3;
                    """.trimIndent(),
                )
            )
            add(
                ReplayCase(
                    name = "overwrite $type then shift",
                    parameters = "values: $type[], index: number",
                    maxResult = 3,
                    body = """
                        if (values.length !== 2 || (index !== 0 && index !== 1)) return 0;
                        values[index] = true;
                        const first = values.shift();
                        if (first === 42 && values[0] === true) return 1;
                        if (first === true && values[0] === 17) return 2;
                        return 3;
                    """.trimIndent(),
                )
            )
        }
        add(
            ReplayCase(
                name = "typed pop payload remains usable as number and array index",
                parameters = "values: number[]",
                maxResult = 2,
                body = """
                    if (values.length !== 1) return 0;
                    const n = values.pop();
                    if (n !== 1) return 1;
                    const target = [10, 20];
                    return Math.floor(n) === 1 && target[n] === 20 ? 2 : -1;
                """.trimIndent(),
            )
        )
        addAll(storageOperationCases())
        addAll(pairCases())
        addAll(typedCases())
    }

    private fun storageOperationCases(): List<ReplayCase> = listOf("any", "unknown").flatMap { type ->
        copyCases(type) + mutationCases(type) + concatCases(type)
    }

    private fun copyCases(type: String): List<ReplayCase> = listOf(
        ReplayCase(
            name = "$type slice() retains all runtime kinds",
            parameters = "values: $type[]",
            maxResult = 6,
            body = """
                if (values.length !== 1) return 0;
                const copy = values.slice();
                const value = copy[0];
                if (value === 42) return 1;
                if (value === true) return 2;
                if (value === false) return 3;
                if (value === null) return 4;
                if (value === undefined) return 5;
                return 6;
            """.trimIndent(),
        ),
        ReplayCase(
            name = "$type slice().reverse() retains all runtime kinds",
            parameters = "values: $type[]",
            maxResult = 6,
            body = """
                if (values.length !== 1) return 0;
                const copy = values.slice().reverse();
                const value = copy[0];
                if (value === 42) return 1;
                if (value === true) return 2;
                if (value === false) return 3;
                if (value === null) return 4;
                if (value === undefined) return 5;
                return 6;
            """.trimIndent(),
        ),
        ReplayCase(
            name = "$type sliced input mixed with appended wrapper",
            parameters = "values: $type[], index: number",
            maxResult = 4,
            body = """
                if (values.length !== 1) return 0;
                const i = Math.floor(index);
                if (!(i >= 0 && i <= 1)) return 0;
                const copy = values.slice();
                copy.push(true);
                const value = copy[i];
                if (i === 1 && value === true) return 1;
                if (i === 0 && value === 42) return 2;
                if (i === 0 && value === false) return 3;
                return 4;
            """.trimIndent(),
        ),
        ReplayCase(
            name = "$type copied payload moves through two shifts",
            parameters = "values: $type[]",
            maxResult = 3,
            body = """
                if (values.length !== 2) return 0;
                const copy = values.slice();
                const first = copy.shift();
                const second = copy.shift();
                if (first === 42 && second === true) return 1;
                if (first === false && second === 17) return 2;
                return 3;
            """.trimIndent(),
        ),
    )

    private fun mutationCases(type: String): List<ReplayCase> = listOf(
        ReplayCase(
            name = "$type unshift preserves unread tail and pop kind",
            parameters = "values: $type[]",
            maxResult = 3,
            body = """
                if (values.length !== 1) return 0;
                const alias = values;
                values.unshift(true);
                const tail = alias.pop();
                if (alias[0] !== true || alias.length !== 1) return -1;
                if (tail === 42) return 1;
                if (tail === false) return 2;
                return 3;
            """.trimIndent(),
        ),
        ReplayCase(
            name = "$type reverse permutes payloads and kinds",
            parameters = "values: $type[]",
            maxResult = 3,
            body = """
                if (values.length !== 2) return 0;
                const copy = values.slice();
                copy.reverse();
                if (copy[0] === 42 && copy[1] === true) return 1;
                if (copy[0] === false && copy[1] === 17) return 2;
                return 3;
            """.trimIndent(),
        ),
        ReplayCase(
            name = "$type fill overrides input kind selectors",
            parameters = "values: $type[], index: number",
            maxResult = 4,
            body = """
                if (values.length !== 2) return 0;
                const i = Math.floor(index);
                if (!(i >= 0 && i <= 1)) return 0;
                values.fill(true, 1, 2);
                const value = values[i];
                if (i === 1 && value === true) return 1;
                if (i === 0 && value === 42) return 2;
                if (i === 0 && value === false) return 3;
                return 4;
            """.trimIndent(),
        ),
    )

    private fun concatCases(type: String): List<ReplayCase> = listOf(
        ReplayCase(
            name = "$type concat copies unread input arrays",
            parameters = "left: $type[], right: $type[]",
            maxResult = 3,
            body = """
                if (left.length !== 1 || right.length !== 1) return 0;
                const copy = left.concat(right);
                const first = copy.shift();
                const second = copy.shift();
                if (first === 42 && second === true) return 1;
                if (first === false && second === 17) return 2;
                return 3;
            """.trimIndent(),
        ),
        ReplayCase(
            name = "$type concat wraps scalar primitives",
            parameters = "values: $type[]",
            maxResult = 3,
            body = """
                if (values.length !== 1) return 0;
                const copy = values.concat(true);
                if (copy[1] !== true) return -1;
                if (copy[0] === 42) return 1;
                if (copy[0] === false) return 2;
                return 3;
            """.trimIndent(),
        ),
        ReplayCase(
            name = "$type empty pop returns undefined and retains length",
            parameters = "values: $type[]",
            maxResult = 1,
            body = """
                if (values.length !== 0) return 0;
                const result = values.pop();
                return result === undefined && values.length === 0 ? 1 : -1;
            """.trimIndent(),
        ),
    )

    private fun pairCases(): List<ReplayCase> = buildList {
        val literals = listOf("42", "true", "false", "null", "undefined", "'left'")
        for (type in listOf("any", "unknown")) {
            for (first in literals) {
                for (second in literals) {
                    // Unconstrained string equality is not modeled yet; write string slots before shifting.
                    val writes = listOf(first, second).mapIndexedNotNull { index, literal ->
                        if (literal == "'left'") "values[$index] = $literal;" else null
                    }.joinToString(separator = "\n")
                    val label = if (writes.isEmpty()) "input pair" else "pair with written string"
                    val maxResult = if (first == "'left'" && second == "'left'") 1 else 2
                    add(
                        ReplayCase(
                            name = "$type $label $first then $second",
                            parameters = "values: $type[]",
                            maxResult = maxResult,
                            body = """
                                if (values.length !== 2) return 0;
                                $writes
                                const first = values.shift();
                                const second = values.shift();
                                return first === $first && second === $second ? 1 : 2;
                            """.trimIndent(),
                        )
                    )
                }
            }
        }
    }

    private fun typedCases(): List<ReplayCase> = buildList {
        for ((type, literal) in listOf("number" to "42", "boolean" to "true")) {
            add(
                ReplayCase(
                    name = "homogeneous $type",
                    parameters = "values: $type[]",
                    maxResult = 2,
                    body = """
                        if (values.length !== 2) return 0;
                        const first = values.shift();
                        return first === $literal && values[0] === $literal ? 1 : 2;
                    """.trimIndent(),
                )
            )
        }
        add(
            ReplayCase(
                name = "written homogeneous string",
                parameters = "values: string[]",
                maxResult = 1,
                body = """
                    if (values.length !== 2) return 0;
                    values[0] = 'left';
                    values[1] = 'right';
                    return values.shift() === 'left' && values[0] === 'right' ? 1 : 2;
                """.trimIndent(),
            )
        )
        add(
            ReplayCase(
                name = "written symbolic union",
                parameters = "values: (number | boolean)[]",
                maxResult = 1,
                body = """
                    if (values.length !== 2) return 0;
                    values[0] = 42;
                    values[1] = true;
                    return values.shift() === 42 && values[0] === true ? 1 : 2;
                """.trimIndent(),
            )
        )
        for (type in listOf("number", "boolean")) {
            for (aliasType in listOf("any", "unknown")) {
                add(
                    ReplayCase(
                        name = "symbolic $type array through $aliasType alias",
                        parameters = "values: $type[]",
                        maxResult = if (type == "number") 2 else 1,
                        body = """
                            if (values.length !== 2) return 0;
                            const alias: $aliasType[] = values;
                            const first = values[0];
                            const second = values[1];
                            return alias.shift() === first && values[0] === second && alias.length === 1 ? 1 : 2;
                        """.trimIndent(),
                    )
                )
            }
        }
        add(
            ReplayCase(
                name = "symbolic object array preserves references",
                parameters = "values: ReplayElement[]",
                maxResult = 2,
                body = """
                    if (values.length !== 2) return 0;
                    const first = values[0];
                    const second = values[1];
                    if (first == null || second == null) return 2;
                    return values.shift() === first && values[0] === second ? 1 : 3;
                """.trimIndent(),
            )
        )
    }

    private fun renderSource(cases: List<ReplayCase>): String = buildString {
        appendLine("class ReplayElement {}")
        appendLine("class ArrayShiftReplay {")
        cases.forEachIndexed { index, case ->
            appendLine("case$index(${case.parameters}) { ${case.body} }")
        }
        appendLine("}")
    }

    private fun jsValue(value: TsTestValue): String = when (value) {
        TsTestValue.TsUndefined -> "undefined"
        TsTestValue.TsNull -> "null"
        is TsTestValue.TsBoolean -> value.value.toString()
        is TsTestValue.TsNumber -> value.number.toString()
        is TsTestValue.TsString -> jsString(value.value)
        is TsTestValue.TsArray<*> -> value.values.joinToString(prefix = "[", postfix = "]", transform = ::jsValue)
        is TsTestValue.TsClass -> value.properties.entries.joinToString(prefix = "({", postfix = "})") {
            "${jsString(it.key)}: ${jsValue(it.value)}"
        }
        else -> error("Unsupported replay value: $value")
    }

    private fun jsString(value: String): String = value.map { "\\u%04x".format(it.code) }.joinToString(
        separator = "",
        prefix = "\"",
        postfix = "\"",
    )

    private fun assertReplay(source: String, index: Int) {
        val script = directory.resolve("replay$index.ts")
        val output = directory.resolve("replay$index.out")
        script.writeText(source)
        val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Replay timed out")
            assertEquals(0, process.exitValue(), "${output.readText()}\n$source")
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private data class ReplayCase(
        val name: String,
        val parameters: String,
        val maxResult: Int,
        val body: String,
    )

    private companion object {
        const val JS_SAME_VALUE = """
            function same(a, b) {
                if (Object.is(a, b)) return true;
                if (!a || !b || typeof a !== 'object' || typeof b !== 'object') return false;
                if (Array.isArray(a) !== Array.isArray(b)) return false;
                const keys = Object.keys(a);
                return keys.length === Object.keys(b).length && keys.every(k => same(a[k], b[k]));
            }
        """

        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
