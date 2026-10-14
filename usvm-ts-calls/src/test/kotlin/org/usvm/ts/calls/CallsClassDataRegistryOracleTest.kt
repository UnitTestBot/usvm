package org.usvm.ts.calls

import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.PropertyDomain
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

class CallsClassDataRegistryOracleTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `registered nested data class preserves nominal identity against a plain object`() {
        val harness = dataClassFixture()
        val function = harness.functions[1]
        val observations = harness.observations[1]
        val original = OriginalTypeScriptClassReplayer(directory, function, observations).use {
            it.replay(inputs = emptyList(), timeoutMillis = 20_000)
        }
        val plainSource = dataClassSource.replace("new Entry(1)", "{ value: 1 }")
        val plain = OriginalTypeScriptClassReplayer(
            sourceRoot = directory,
            function = function,
            observationFields = observations,
            sourceOverride = plainSource,
            overrideSourceFile = "Entries.ts",
        ).use { it.replay(inputs = emptyList(), timeoutMillis = 20_000) }

        val entry = JsConcreteValue.Object(
            fields = mapOf("value" to number(1), CLASS_IDENTITY_FIELD to JsConcreteValue.String("entry")),
        )
        val plainEntry = entry.copy(fields = entry.fields + (CLASS_IDENTITY_FIELD to JsConcreteValue.String("plain")))
        assertTrue(original.observation.representable, original.toString())
        assertTrue(plain.observation.representable, plain.toString())
        assertEquals(JsConcreteValue.Array(listOf(entry)), original.observation.values["result_0"])
        assertEquals(JsConcreteValue.Array(listOf(plainEntry)), plain.observation.values["result_0"])
        assertEquals(JsConcreteValue.String("target"), original.observation.values["state_1_$CLASS_IDENTITY_FIELD"])
        assertFalse(
            compareCallsClassObservations(original.observation, original.observation, plain.observation).accepted
        )

        val self = OriginalTypeScriptClassReplayer(directory, harness.functions[2], harness.observations[2]).use {
            it.replay(inputs = emptyList(), timeoutMillis = 20_000)
        }
        val selfResult = self.observation.values["result_0"] as JsConcreteValue.Object
        assertTrue(self.observation.representable, self.toString())
        assertEquals(JsConcreteValue.String("target"), selfResult.fields[CLASS_IDENTITY_FIELD])
        assertEquals(JsConcreteValue.Array(listOf(entry)), selfResult.fields["items"])
    }

    @Test
    fun `registered nested data class rejects unlisted prototypes and changed own fields`() {
        val harness = dataClassFixture()
        val replacements = listOf(
            "new OtherEntry(1)" to "Unlisted object prototype",
            "new ChildEntry(1)" to "Unlisted object prototype",
            "Object.assign(new Entry(1), { extra: 2 })" to "Registered data-class own fields changed",
            "Object.defineProperty(new Entry(1), 'value', { get: () => 1 })" to
                "Registered data-class field is not an ordinary data property",
            "new Date(1)" to "Unlisted object prototype",
        )
        for ((replacement, expectedDiagnostic) in replacements) {
            val changed = dataClassSource.replace("new Entry(1)", replacement) + """

                class OtherEntry { value: number; constructor(value: number) { this.value = value; } }
                class ChildEntry extends Entry {}
            """.trimIndent()
            val replay = OriginalTypeScriptClassReplayer(
                sourceRoot = directory,
                function = harness.functions[1],
                observationFields = harness.observations[1],
                sourceOverride = changed,
                overrideSourceFile = "Entries.ts",
            ).use { it.replay(inputs = emptyList(), timeoutMillis = 20_000) }

            assertFalse(replay.observation.representable, replay.toString())
            assertTrue(replay.observation.diagnostic?.contains(expectedDiagnostic) == true, replay.toString())
        }
    }

    @Test
    fun `registered data class rejects constructor and prototype mutation`() {
        val harness = dataClassFixture()
        val mutations = listOf(
            "(Entry as any).hidden = 2;",
            "(Entry.prototype as any).hidden = 2;",
            "Object.defineProperty(Entry.prototype, 'value', { get: () => 1 });",
            "Object.setPrototypeOf(Entry.prototype, {});",
            "Object.defineProperty(Entry, 'name', { get: () => 'Entry' });",
            "Object.defineProperty(Entry, 'name', { value: 'Changed' });",
            "Object.defineProperty(Entry, 'length', { value: 99 });",
        )
        for (mutation in mutations) {
            val changed = dataClassSource.replace("read(): Entry[] {", "read(): Entry[] { $mutation")
            val replay = OriginalTypeScriptClassReplayer(
                sourceRoot = directory,
                function = harness.functions[1],
                observationFields = harness.observations[1],
                sourceOverride = changed,
                overrideSourceFile = "Entries.ts",
            ).use { it.replay(inputs = emptyList(), timeoutMillis = 20_000) }

            assertFalse(replay.observation.representable, replay.toString())
            val changedContract = replay.observation.diagnostic?.contains(
                "Registered data-class constructor or prototype changed"
            ) == true
            assertTrue(changedContract, replay.toString())
        }
    }

    @Test
    fun `registered class fields require the exact frozen observation domain`() {
        val harness = dataClassFixture(observationFieldDomain = IntegerDomain(min = 0, max = 2))

        val replay = OriginalTypeScriptClassReplayer(directory, harness.functions[1], harness.observations[1]).use {
            it.replay(inputs = emptyList(), timeoutMillis = 20_000)
        }

        assertFalse(replay.observation.representable, replay.toString())
        assertTrue(
            replay.observation.diagnostic?.contains("Registered data-class observation domain changed") == true,
            replay.toString(),
        )
    }

    @Test
    fun `registry identity field collisions reject before execution`() {
        val entryFields = mapOf("value" to NumberDomain())
        val registry = listOf(CallsClassDataClass(id = "entry", exportName = "Entry", fields = entryFields))
        val collision = ObjectDomain(fields = mapOf(CLASS_IDENTITY_FIELD to NumberDomain()))

        assertFailsWith<IllegalArgumentException> {
            surface().copy(stateFields = mapOf("nested" to ArrayDomain(element = collision)), dataClasses = registry)
        }
        assertFailsWith<IllegalArgumentException> {
            surface().copy(dataClasses = registry + registry)
        }
        assertFailsWith<IllegalArgumentException> {
            surface().copy(dataClasses = listOf(registry.single().copy(fields = collision.fields)))
        }
        assertFailsWith<IllegalArgumentException> {
            surface().copy(dataClasses = listOf(registry.single().copy(id = "plain")))
        }
    }

    @Test
    fun `symbolic nested registered instances and target identity agree with native observations`() {
        val harness = dataClassFixture()
        val result = CurrentTsCallsClassCoverageEngine().search(
            CallsClassSearchRequest(
                harness = harness,
                profile = CallsExperimentProfile.EMPTY_STOP,
                frozenModelIds = emptySet(),
                expectedNativeFrontendRevision = CallsBuildIdentity.nativeFrontendRevision,
                seed = 1,
                budget = 3.seconds,
                timeQuantum = 100.milliseconds,
                solverQueryLimit = 1.seconds,
                solverType = SolverType.YICES,
                candidateCap = 10,
                requireEntryPointWitness = true,
            ),
        )

        assertEquals(harness.scenarios.map { it.id }.toSet(), result.search.candidates.map { it.entryPointId }.toSet())
        for (candidate in result.search.candidates) {
            val index = harness.scenarios.indexOfFirst { it.id == candidate.entryPointId }
            val original = OriginalTypeScriptClassReplayer(
                directory,
                harness.functions[index],
                harness.observations[index],
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = 20_000) }
            val instrumented = OriginalTypeScriptClassReplayer(
                sourceRoot = directory,
                function = harness.functions[index],
                observationFields = harness.observations[index],
                sourceOverride = dataClassSource.replace(
                    "return this.items;",
                    "; (globalThis as any).entryHits?.add('read'); return this.items;",
                ),
                overrideSourceFile = "Entries.ts",
                counterGlobalName = "entryHits",
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = 20_000) }

            val checked = compareCallsClassObservations(
                candidate.observation,
                original.observation,
                instrumented.observation
            )
            assertTrue(checked.accepted, "$candidate / $original / $instrumented / $checked")
        }
    }

    @Test
    fun `boxed conditional array elements retain registered or plain identity on both branches`() {
        val conditionalSource = dataClassSource
            .replace("items: Entry[];", "items: any[];")
            .replace(
                "constructor() { this.items = [new Entry(1)]; }",
                "constructor(flag: boolean) { this.items = [flag ? new Entry(1) : { value: 1 }]; }",
            )
        val harness = dataClassFixture(
            fixtureSource = conditionalSource,
            constructorInputs = listOf(PropertyInput(name = "flag", domain = BooleanDomain)),
        )

        val result = CurrentTsCallsClassCoverageEngine().search(
            CallsClassSearchRequest(
                harness = harness,
                profile = CallsExperimentProfile.EMPTY_STOP,
                frozenModelIds = emptySet(),
                expectedNativeFrontendRevision = CallsBuildIdentity.nativeFrontendRevision,
                seed = 1,
                budget = 3.seconds,
                timeQuantum = 100.milliseconds,
                solverQueryLimit = 1.seconds,
                solverType = SolverType.YICES,
                candidateCap = 20,
                requireEntryPointWitness = true,
            ),
        )

        val constructorScenarioId = harness.scenarios.first().id
        val constructorBranches = result.search.candidates.filter { it.entryPointId == constructorScenarioId }
            .map { it.inputs.single() }.toSet()
        assertEquals(setOf(JsConcreteValue.Boolean(true), JsConcreteValue.Boolean(false)), constructorBranches)
        assertEquals(harness.scenarios.map { it.id }.toSet(), result.search.candidates.map { it.entryPointId }.toSet())
        for (candidate in result.search.candidates) {
            val index = harness.scenarios.indexOfFirst { it.id == candidate.entryPointId }
            val original = OriginalTypeScriptClassReplayer(
                directory,
                harness.functions[index],
                harness.observations[index],
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = 20_000) }
            val instrumented = OriginalTypeScriptClassReplayer(
                sourceRoot = directory,
                function = harness.functions[index],
                observationFields = harness.observations[index],
                sourceOverride = conditionalSource.replace(
                    "return this.items;",
                    "; (globalThis as any).entryHits?.add('read'); return this.items;",
                ),
                overrideSourceFile = "Entries.ts",
                counterGlobalName = "entryHits",
            ).use { it.replay(inputs = candidate.inputs, timeoutMillis = 20_000) }

            val checked = compareCallsClassObservations(
                candidate.observation,
                original.observation,
                instrumented.observation
            )
            assertTrue(checked.accepted, "$candidate / $original / $instrumented / $checked")
            val createsRegisteredEntry = (candidate.inputs.single() as JsConcreteValue.Boolean).value
            val expectedIdentity = if (createsRegisteredEntry) "entry" else "plain"
            val symbolic = requireNotNull(candidate.observation)
            val items = symbolic.values.getValue("state_0_items") as JsConcreteValue.Array
            val observedEntry = items.elements.single() as JsConcreteValue.Object
            assertEquals(JsConcreteValue.String(expectedIdentity), observedEntry.fields[CLASS_IDENTITY_FIELD])
        }
    }

    @Test
    fun `frozen registry domains compare fields without relying on insertion order`() {
        Files.writeString(
            directory.resolve("Entries.ts"),
            """
            export class PairEntry {
              key: number;
              value: number;
              constructor(key: number, value: number) { this.key = key; this.value = value; }
            }
            export class Entries {
              item: PairEntry = new PairEntry(1, 2);
            }
            """.trimIndent()
        )
        val registryFields = mapOf("key" to NumberDomain(), "value" to NumberDomain())
        val observationFields = registryFields.entries.reversed().associate { it.toPair() }
        val surface = CallsClassSurface(
            sourceFile = "Entries.ts",
            exportName = "Entries",
            constructorInputs = emptyList(),
            members = emptyList(),
            stateFields = mapOf("item" to ObjectDomain(fields = observationFields)),
            dataClasses = listOf(CallsClassDataClass(id = "pair", exportName = "PairEntry", fields = registryFields)),
        )
        val harness = prepareCallsClassHarness(directory, project(), surface, maxSequenceLength = 1)

        val replay = OriginalTypeScriptClassReplayer(
            directory,
            harness.functions.single(),
            harness.observations.single()
        )
            .use { it.replay(inputs = emptyList(), timeoutMillis = 20_000) }

        assertTrue(replay.observation.representable, replay.toString())
        val item = replay.observation.values.getValue("state_0_item") as JsConcreteValue.Object
        assertEquals(number(1), item.fields["key"])
        assertEquals(number(2), item.fields["value"])
        assertEquals(JsConcreteValue.String("pair"), item.fields[CLASS_IDENTITY_FIELD])
    }

    private fun dataClassFixture(
        observationFieldDomain: PropertyDomain = NumberDomain(),
        fixtureSource: String = dataClassSource,
        constructorInputs: List<PropertyInput> = emptyList(),
    ): CallsClassPreparedHarness {
        Files.writeString(directory.resolve("Entries.ts"), fixtureSource)
        val entryFields = mapOf("value" to NumberDomain())
        val array = ArrayDomain(
            element = ObjectDomain(fields = mapOf("value" to observationFieldDomain)),
            maxLength = 4,
        )
        val stateFields = mapOf("items" to array)
        val surface = CallsClassSurface(
            sourceFile = "Entries.ts",
            exportName = "Entries",
            constructorInputs = constructorInputs,
            members = listOf(
                CallsClassMember(
                    id = "read",
                    name = "read",
                    kind = CallsClassMemberKind.METHOD,
                    inputs = emptyList(),
                    resultDomain = array,
                ),
                CallsClassMember(
                    id = "self",
                    name = "self",
                    kind = CallsClassMemberKind.METHOD,
                    inputs = emptyList(),
                    resultDomain = ObjectDomain(fields = stateFields),
                    resultClassName = "Entries",
                ),
            ),
            stateFields = stateFields,
            dataClasses = listOf(CallsClassDataClass(id = "entry", exportName = "Entry", fields = entryFields)),
        )

        return prepareCallsClassHarness(directory, project(), surface, maxSequenceLength = 1)
    }

    private val dataClassSource = """
        export class Entry {
          value: number;
          constructor(value: number) { this.value = value; }
        }
        export class Entries {
          items: Entry[];
          constructor() { this.items = [new Entry(1)]; }
          read(): Entry[] { return this.items; }
          self(): Entries { return this; }
        }
    """.trimIndent()

    private fun surface() = CallsClassSurface(
        sourceFile = "Entries.ts",
        exportName = "Entries",
        constructorInputs = emptyList(),
        members = emptyList(),
        stateFields = emptyMap(),
    )

    private fun project() = CallsProjectCase(
        projectId = "fixture",
        revision = "0".repeat(40),
        sourceRoot = ".",
        development = false,
        functions = emptyList(),
    )

    private fun number(value: Int) = JsConcreteValue.number(value.toDouble())
}
