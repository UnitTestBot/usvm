package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyInput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CallsClassHarnessOracleTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `scenario schedule freezes constructor every member and every ordered pair`() {
        val harness = fixture()

        assertEquals(expected = 21, actual = harness.scenarios.size)
        assertEquals(emptyList(), harness.scenarios.first().memberIds)
        assertEquals(
            listOf("add", "read", "write", "arrow"),
            harness.scenarios.drop(1).take(4).map { it.memberIds.single() }
        )
        assertTrue(harness.scenarios.any { it.memberIds == listOf("write", "read") })
        assertTrue(harness.scenarios.any { it.memberIds == listOf("add", "add") })
        assertTrue(harness.functions.all { it.symbolicCoverageClass?.className == "Counter" })
        assertTrue(harness.functions[1].inputs[1].domain is ObjectDomain)
    }

    @Test
    fun `same receiver observes setter mutation before getter and resets between replays`() {
        val harness = fixture()
        val index = harness.scenarios.indexOfFirst { it.memberIds == listOf("write", "read") }
        OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[index],
            observationFields = harness.observations[index],
        ).use { replayer ->
            val first = replayer.replay(inputs = listOf(number(2), number(9)), timeoutMillis = 20_000)
            val second = replayer.replay(inputs = listOf(number(3), number(4)), timeoutMillis = 20_000)

            assertTrue(first.observation.representable, first.observation.diagnostic)
            assertEquals(number(2), first.observation.values["state_0_count"])
            assertEquals(number(9), first.observation.values["state_1_count"])
            assertEquals(number(9), first.observation.values["result_1"])
            assertEquals(JsConcreteValue.Undefined, first.observation.values["result_0"])
            assertEquals(number(3), second.observation.values["state_0_count"])
            assertEquals(number(4), second.observation.values["result_1"])
        }
    }

    @Test
    fun `object parameter transport stays frozen when callable mutates it`() {
        val harness = fixture()
        val index = harness.scenarios.indexOfFirst { it.memberIds == listOf("add", "read") }
        val options = JsConcreteValue.Object(mapOf("amount" to number(3)))
        val inputs = listOf(number(2), options)

        val replay = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[index],
            observationFields = harness.observations[index],
        ).use { it.replay(inputs = inputs, timeoutMillis = 20_000) }

        assertEquals(inputs, replay.inputs)
        assertTrue(replay.observation.representable, replay.observation.diagnostic)
        assertEquals(number(5), replay.observation.values["result_0"])
        assertEquals(number(5), replay.observation.values["result_1"])
    }

    @Test
    fun `exact counters come only from equivalent instrumented scenario execution`() {
        val harness = fixture()
        val index = harness.scenarios.indexOfFirst { it.memberIds == listOf("arrow") }
        val inputs = listOf(number(2), number(5))
        val source = Files.readString(directory.resolve("Counter.ts"))
        val instrumented = source.replace(
            "return this.count * x;",
            "(globalThis as any).classHits?.add('arrow-body'); return this.count * x;"
        )
        val original = OriginalTypeScriptClassReplayer(
            directory,
            harness.functions[index],
            harness.observations[index]
        ).use {
            it.replay(inputs = inputs, timeoutMillis = 20_000)
        }
        val counted = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[index],
            observationFields = harness.observations[index],
            sourceOverride = instrumented,
            overrideSourceFile = "Counter.ts",
            counterGlobalName = "classHits",
        ).use { it.replay(inputs = inputs, timeoutMillis = 20_000) }

        assertEquals(setOf("arrow-body"), counted.coveredStatementKeys)
        assertTrue(
            compareCallsClassObservations(original.observation, original.observation, counted.observation).accepted
        )
    }

    @Test
    fun `returned values and receiver state are independently checked`() {
        val original = CallsClassObservation(
            completion = CallsCoverageCompletion.RETURNED,
            values = mapOf("result_0" to number(1), "state_1_count" to number(2)),
        )
        val wrongResult = original.copy(values = original.values + ("result_0" to number(0)))
        val wrongState = original.copy(values = original.values + ("state_1_count" to number(0)))

        assertFalse(compareCallsClassObservations(wrongResult, original, original).accepted)
        assertFalse(compareCallsClassObservations(wrongState, original, original).accepted)
        assertFalse(compareCallsClassObservations(original, original, wrongState).accepted)
        assertTrue(compareCallsClassObservations(original, original, original).accepted)
    }

    @Test
    fun `matching unavailable outcomes are never equivalent`() {
        val unavailable = CallsClassObservation(
            completion = CallsCoverageCompletion.THREW,
            representable = false,
            diagnostic = "Incomplete thrown receiver state",
        )

        assertFalse(compareCallsClassObservations(unavailable, unavailable, unavailable).accepted)
        assertFalse(compareCallsClassObservations(null, unavailable, unavailable).accepted)
    }

    @Test
    fun `invalid getter and sequence bound reject before execution`() {
        assertFailsWith<IllegalArgumentException> {
            surface().copy(
                members = listOf(
                    CallsClassMember(
                        id = "bad",
                        name = "bad",
                        kind = CallsClassMemberKind.GETTER,
                        inputs = listOf(PropertyInput("x", IntegerDomain())),
                        resultDomain = NumberDomain(),
                    )
                )
            )
        }
        Files.writeString(directory.resolve("Counter.ts"), source)

        assertFailsWith<IllegalArgumentException> {
            prepareCallsClassHarness(directory, project(), surface(), maxSequenceLength = 3)
        }
    }

    @Test
    fun `array result is snapshotted before subsequent mutation`() {
        Files.writeString(
            directory.resolve("Bag.ts"),
            """
            export class Bag {
              items: number[] = [1];
              take(): number[] { return this.items; }
              replace(x: number): number { this.items[0] = x; return x; }
            }
            """.trimIndent()
        )
        val arrayDomain = ArrayDomain(element = NumberDomain(), maxLength = 4)
        val surface = CallsClassSurface(
            sourceFile = "Bag.ts",
            exportName = "Bag",
            constructorInputs = emptyList(),
            members = listOf(
                CallsClassMember("take", "take", CallsClassMemberKind.METHOD, emptyList(), arrayDomain),
                CallsClassMember(
                    "replace",
                    "replace",
                    CallsClassMemberKind.METHOD,
                    listOf(PropertyInput("x", NumberDomain())),
                    NumberDomain()
                ),
            ),
            stateFields = mapOf("items" to arrayDomain),
        )
        val harness = prepareCallsClassHarness(directory, project(), surface)
        val index = harness.scenarios.indexOfFirst { it.memberIds == listOf("take", "replace") }

        val replay = OriginalTypeScriptClassReplayer(
            directory,
            harness.functions[index],
            harness.observations[index]
        ).use {
            it.replay(inputs = listOf(number(9)), timeoutMillis = 20_000)
        }

        assertTrue(replay.observation.representable, replay.observation.diagnostic)
        assertEquals(JsConcreteValue.Array(listOf(number(1))), replay.observation.values["result_0"])
        assertEquals(JsConcreteValue.Array(listOf(number(1))), replay.observation.values["state_1_items"])
        assertEquals(JsConcreteValue.Array(listOf(number(9))), replay.observation.values["state_2_items"])
    }

    @Test
    fun `symbolic constructed state and return snapshots agree with original replay`() {
        Files.writeString(
            directory.resolve("Scalar.ts"),
            """
            export class Scalar {
              count: number;
              constructor(start: number) { this.count = start; }
              add(amount: number): number { this.count += amount; return this.count; }
              read(): number { return this.count; }
            }
            """.trimIndent()
        )
        val surface = CallsClassSurface(
            sourceFile = "Scalar.ts",
            exportName = "Scalar",
            constructorInputs = listOf(PropertyInput("start", IntegerDomain(min = 0, max = 4))),
            members = listOf(
                CallsClassMember(
                    "add",
                    "add",
                    CallsClassMemberKind.METHOD,
                    listOf(PropertyInput("amount", IntegerDomain(min = 0, max = 4))),
                    NumberDomain()
                ),
                CallsClassMember("read", "read", CallsClassMemberKind.METHOD, emptyList(), NumberDomain()),
            ),
            stateFields = mapOf("count" to NumberDomain()),
        )
        val harness = prepareCallsClassHarness(directory, project(), surface, maxSequenceLength = 2)

        val result = CurrentTsCallsClassCoverageEngine().search(
            CallsClassSearchRequest(
                harness = harness,
                profile = CallsExperimentProfile.EMPTY_STOP,
                frozenModelIds = emptySet(),
                expectedNativeFrontendRevision = CallsBuildIdentity.nativeFrontendRevision,
                seed = 1,
                budget = 2.seconds,
                timeQuantum = 100.milliseconds,
                solverQueryLimit = 1.seconds,
                solverType = SolverType.YICES,
                candidateCap = 20,
                requireEntryPointWitness = true,
            )
        )

        assertTrue(result.search.candidates.isNotEmpty(), result.toString())
        assertTrue((result.solverCalls ?: 0) > 0, result.toString())
        assertTrue(result.solverQueries.isNotEmpty(), result.toString())
        assertTrue(result.timeQuanta.isNotEmpty(), result.toString())
        assertTrue(
            result.timeQuanta.all { it.requestedQuantumMillis <= 100 && it.elapsedNanos >= 0 },
            result.toString()
        )
        assertTrue(result.search.executedSteps > 0, result.toString())
        assertTrue(result.search.searchElapsedMillis <= 3_000, result.toString())
        assertEquals(
            harness.scenarios.map { it.id }.toSet(),
            result.search.candidates.map { it.entryPointId }.toSet(),
            result.toString()
        )
        for (candidate in result.search.candidates) {
            val index = harness.scenarios.indexOfFirst { it.id == candidate.entryPointId }
            val original = OriginalTypeScriptClassReplayer(
                directory,
                harness.functions[index],
                harness.observations[index]
            ).use {
                it.replay(candidate.inputs, timeoutMillis = 20_000)
            }
            val checked =
                compareCallsClassObservations(candidate.observation, original.observation, original.observation)
            assertTrue(checked.accepted, "$candidate / $original / $checked")
        }
    }

    @Test
    fun `optional array and nested object snapshots guard nullish values before copying`() {
        Files.writeString(
            directory.resolve("OptionalBag.ts"),
            """
            export class OptionalBag {
              items: number[] | undefined = undefined;
              object: { items: number[] } | null = null;
              read(): number[] | undefined { return this.items; }
            }
            """.trimIndent()
        )
        val array = ArrayDomain(element = NumberDomain(), maxLength = 4)
        val optionalArray = OptionalDomain(value = array, nil = JsConcreteValue.Undefined)
        val surface = CallsClassSurface(
            sourceFile = "OptionalBag.ts",
            exportName = "OptionalBag",
            constructorInputs = emptyList(),
            members = listOf(CallsClassMember("read", "read", CallsClassMemberKind.METHOD, emptyList(), optionalArray)),
            stateFields = mapOf(
                "items" to optionalArray,
                "object" to OptionalDomain(value = ObjectDomain(mapOf("items" to array)), nil = JsConcreteValue.Null),
            ),
        )
        val harness = prepareCallsClassHarness(directory, project(), surface, maxSequenceLength = 1)

        val replay = OriginalTypeScriptClassReplayer(directory, harness.functions[1], harness.observations[1]).use {
            it.replay(inputs = emptyList(), timeoutMillis = 20_000)
        }

        assertTrue(replay.observation.representable, replay.observation.diagnostic)
        assertEquals(JsConcreteValue.Undefined, replay.observation.values["result_0"])
        assertEquals(JsConcreteValue.Null, replay.observation.values["state_1_object"])
    }

    @Test
    fun `raw output and receiver shape checks reject fields hidden by projection`() {
        Files.writeString(
            directory.resolve("Hidden.ts"),
            """
            export class Hidden {
              count: number = 1;
              result(): { kept: number; omitted: number } { return { kept: 1, omitted: 2 }; }
              mutate(): number { (this as any).extra = 3; return this.count; }
              mutateCallback(): number { (this as any).extraCallback = () => 3; return this.count; }
            }
            """.trimIndent()
        )
        val surface = CallsClassSurface(
            sourceFile = "Hidden.ts",
            exportName = "Hidden",
            constructorInputs = emptyList(),
            members = listOf(
                CallsClassMember(
                    "result",
                    "result",
                    CallsClassMemberKind.METHOD,
                    emptyList(),
                    ObjectDomain(mapOf("kept" to NumberDomain()))
                ),
                CallsClassMember("mutate", "mutate", CallsClassMemberKind.METHOD, emptyList(), NumberDomain()),
                CallsClassMember(
                    id = "mutateCallback",
                    name = "mutateCallback",
                    kind = CallsClassMemberKind.METHOD,
                    inputs = emptyList(),
                    resultDomain = NumberDomain(),
                ),
            ),
            stateFields = mapOf("count" to NumberDomain()),
        )
        val harness = prepareCallsClassHarness(directory, project(), surface, maxSequenceLength = 1)

        for (index in 1..3) {
            val replay = OriginalTypeScriptClassReplayer(
                directory,
                harness.functions[index],
                harness.observations[index]
            ).use {
                it.replay(inputs = emptyList(), timeoutMillis = 20_000)
            }

            assertFalse(replay.observation.representable)
            assertTrue(
                replay.observation.diagnostic?.contains("Unlisted or missing object property") == true,
                replay.toString()
            )
        }
    }

    @Test
    fun `native empty object shape rejects builtin internal slot values`() {
        Files.writeString(
            directory.resolve("Builtin.ts"),
            """
                export class Builtin {
                  result(): object { return new Date(1234); }
                }
            """.trimIndent(),
        )
        val surface = CallsClassSurface(
            sourceFile = "Builtin.ts",
            exportName = "Builtin",
            constructorInputs = emptyList(),
            members = listOf(
                CallsClassMember(
                    id = "result",
                    name = "result",
                    kind = CallsClassMemberKind.METHOD,
                    inputs = emptyList(),
                    resultDomain = ObjectDomain(fields = emptyMap()),
                ),
            ),
            stateFields = emptyMap(),
        )
        val harness = prepareCallsClassHarness(directory, project(), surface, maxSequenceLength = 1)

        val replay = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = harness.functions[1],
            observationFields = harness.observations[1],
        ).use { it.replay(inputs = emptyList(), timeoutMillis = 20_000) }

        assertFalse(replay.observation.representable)
        assertTrue(replay.observation.diagnostic?.contains("Unlisted object prototype") == true)
    }

    private fun fixture(): CallsClassPreparedHarness {
        Files.writeString(directory.resolve("Counter.ts"), source)
        return prepareCallsClassHarness(directory, project(), surface())
    }

    private fun surface(): CallsClassSurface = CallsClassSurface(
        sourceFile = "Counter.ts",
        exportName = "Counter",
        constructorInputs = listOf(PropertyInput("start", IntegerDomain(min = 0, max = 10))),
        members = listOf(
            CallsClassMember(
                "add",
                "add",
                CallsClassMemberKind.METHOD,
                listOf(PropertyInput("options", ObjectDomain(mapOf("amount" to IntegerDomain(min = 0, max = 10))))),
                NumberDomain()
            ),
            CallsClassMember("read", "value", CallsClassMemberKind.GETTER, emptyList(), NumberDomain()),
            CallsClassMember(
                "write",
                "value",
                CallsClassMemberKind.SETTER,
                listOf(PropertyInput("value", IntegerDomain(min = 0, max = 10))),
                ConstantDomain(JsConcreteValue.Undefined)
            ),
            CallsClassMember(
                "arrow",
                "multiply",
                CallsClassMemberKind.FUNCTION_FIELD,
                listOf(PropertyInput("x", IntegerDomain(min = 0, max = 10))),
                NumberDomain()
            ),
        ),
        stateFields = mapOf("count" to NumberDomain()),
    )

    private fun project() = CallsProjectCase(
        "fixture",
        "0".repeat(40),
        ".",
        development = false,
        functions = emptyList()
    )

    private fun number(value: Int) = JsConcreteValue.number(value.toDouble())

    private val source = """
        export class Counter {
          count: number;
          constructor(start: number) { this.count = start; }
          add(options: { amount: number }): number {
            this.count += options.amount; options.amount = 99; return this.count;
          }
          get value(): number { return this.count; }
          set value(value: number) { this.count = value; }
          multiply = (x: number): number => { return this.count * x; };
        }
    """.trimIndent()
}
