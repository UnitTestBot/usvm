package org.usvm.ts.calls

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.io.TempDir
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallsSparseObservationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `known hole cannot be emitted as its stale payload`() {
        val result = observe(methodName = "sparse", element = NumberDomain())

        assertFalse(result.representable)
        assertTrue(result.values.isEmpty())
        assertTrue(result.diagnostic.orEmpty().contains("Sparse array slot 0"), result.toString())
        assertFalse(compareCallsClassObservations(result, result, result).accepted)
    }

    @Test
    fun `dense arrays remain representable`() {
        val result = observe(methodName = "dense", element = NumberDomain())

        assertTrue(result.representable, result.toString())
        assertEquals(
            JsConcreteValue.Array(elements = listOf(JsConcreteValue.number(7.0), JsConcreteValue.number(11.0))),
            result.values["values"],
        )
    }

    @Test
    fun `boxed undefined output remains explicitly unsupported`() {
        val result = observe(methodName = "explicitUndefined", element = OptionalDomain(value = NumberDomain()))

        assertFalse(result.representable)
        assertTrue(result.diagnostic.orEmpty().contains("BOXED"), result.toString())
    }

    @Test
    fun `typed array return cannot hide an override written through a widened alias`() {
        val result = observe(methodName = "boxedAlias", element = OptionalDomain(value = NumberDomain()))

        assertFalse(result.representable)
        assertTrue(result.diagnostic.orEmpty().contains("BOXED"), result.toString())
    }

    @Test
    fun `own undefined in unresolved storage remains representable`() {
        val result = observe(methodName = "unresolvedUndefined", element = OptionalDomain(value = NumberDomain()))

        assertTrue(result.representable, result.toString())
        assertEquals(
            JsConcreteValue.Array(elements = listOf(JsConcreteValue.Undefined, JsConcreteValue.number(11.0))),
            result.values["values"],
        )
    }

    @Test
    fun `native oracle distinguishes deleted own slot from explicit undefined`() {
        writeFixture()
        val property = PropertyDefinition(
            id = PropertyId("sparse-observation.native"),
            inputs = listOf(PropertyInput(name = "value", domain = ConstantDomain(JsConcreteValue.number(0.0)))),
            predicate = TypeScriptEntryPoint(module = "SparseObservation.ts", exportName = "nativeOracle"),
        )

        val result = FastCheckBackend(sourceRoots = listOf(directory)).run(
            property = property,
            configuration = PropertyRunConfiguration(seed = 42, numRuns = 1, timeoutMillis = 10_000),
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
    }

    private fun observe(methodName: String, element: PropertyDomain): CallsClassObservation {
        val source = writeFixture()
        val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == methodName }

        return TsMachine(scene = scene, tsOptions = TsOptions(maxArraySize = 4)).use { machine ->
            val outcome = machine.analyzeWithMetadata(methods = listOf(method))

            assertFalse(outcome.engineFailed)
            assertTrue(outcome.unsupportedPaths.isEmpty(), outcome.toString())
            extractCallsClassObservation(
                state = outcome.states.single(),
                method = method,
                fields = mapOf("values" to ArrayDomain(element = element, maxLength = 4)),
            )
        }
    }

    private fun writeFixture(): Path {
        val source = directory.resolve("SparseObservation.ts")
        Files.writeString(source, SOURCE)
        return source
    }

    private companion object {
        val SOURCE = """
            export class SparseObservation {
                static sparse(): { values: number[] } {
                    const values = [7, 11];
                    delete values[0];
                    return { values };
                }

                static dense(): { values: number[] } {
                    return { values: [7, 11] };
                }

                static explicitUndefined(): { values: any[] } {
                    return { values: [undefined, 11] };
                }

                static unresolvedUndefined(): { values: any[] } {
                    const values: any[] = [];
                    values.push(undefined);
                    values.push(11);
                    return { values };
                }

                static boxedAlias(): { values: number[] } {
                    const values = [7, 11];
                    const alias: any[] = values;
                    alias[0] = undefined;
                    return { values };
                }
            }

            export function nativeOracle(_value: number): boolean {
                const sparse = SparseObservation.sparse().values;
                const explicit = SparseObservation.explicitUndefined().values;
                const alias = SparseObservation.boxedAlias().values;
                return !(0 in sparse) && (1 in sparse) && sparse[0] === undefined &&
                    (0 in explicit) && explicit[0] === undefined &&
                    (0 in alias) && alias[0] === undefined;
            }
        """.trimIndent()
    }
}
