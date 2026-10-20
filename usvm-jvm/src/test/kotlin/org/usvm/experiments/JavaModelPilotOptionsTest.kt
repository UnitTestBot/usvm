package org.usvm.experiments

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.usvm.PathSelectionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JavaModelPilotOptionsTest {
    @Test
    fun `explicit search settings select weighted random uncovered distance`() {
        val manifest = Json.parseToJsonElement(
            """
                {
                    "pathSelectionStrategy": "CLOSEST_TO_UNCOVERED_RANDOM",
                    "solverLimitMillis": 1000,
                    "timeQuantumMillis": 1000,
                    "seed": 23
                }
            """.trimIndent(),
        ).jsonObject

        val options = pilotMachineOptions(manifest = manifest, budgetMillis = 30_000)

        assertEquals(listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM), options.pathSelectionStrategies)
        assertEquals(1.seconds, options.solverTimeout)
        assertEquals(1.seconds, options.maxEntryPointTimeQuantum)
        assertEquals(23L, options.randomSeed)
        assertEquals(30.seconds, options.timeout)
    }

    @Test
    fun `historical manifests retain their recorded defaults`() {
        val manifest = Json.parseToJsonElement("{}").jsonObject

        val options = pilotMachineOptions(manifest = manifest, budgetMillis = 30_000)

        assertEquals(listOf(PathSelectionStrategy.BFS), options.pathSelectionStrategies)
        assertEquals(250.milliseconds, options.maxEntryPointTimeQuantum)
        assertEquals(1.seconds, options.solverTimeout)
        assertEquals(17L, options.randomSeed)
    }

    @Test
    fun `invalid search settings fail before machine construction`() {
        val invalidManifests = listOf(
            """{"pathSelectionStrategy":"UNKNOWN"}""",
            """{"solverLimitMillis":0}""",
            """{"timeQuantumMillis":30001}""",
        )

        invalidManifests.forEach { encoded ->
            val manifest = Json.parseToJsonElement(encoded).jsonObject

            assertFailsWith<IllegalArgumentException> {
                pilotMachineOptions(manifest = manifest, budgetMillis = 30_000)
            }
        }
    }
}
