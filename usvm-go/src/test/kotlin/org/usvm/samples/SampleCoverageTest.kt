package org.usvm.samples

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.usvm.generatedGoFile
import org.usvm.model.Converter
import org.usvm.model.Parser
import kotlin.test.assertEquals

class SampleCoverageTest {
    @Test
    fun everyExportedSampleHasAnExplicitTest() {
        val samples = sampleClasses.flatMap { clazz ->
            clazz.declaredMethods.mapNotNull { it.getAnnotation(GoSample::class.java) }
        }
        for (fixture in listOf("examples", "regressions")) {
            val pkg = Converter.unpack(Parser().deserialize(generatedGoFile("$fixture/usvm_$fixture.json").path))
            val expected = pkg.methods.filter { '$' !in it.metName }.map { it.metName }.toSet()
            val registered = samples.filter { it.fixture == fixture }.map { it.method }

            assertEquals(expected, registered.toSet(), "Every $fixture method must have a semantic test")
            assertEquals(registered.toSet().size, registered.size, "Each $fixture method must be registered once")
        }
    }

    @Test
    fun everyConstantRegressionHasANativeOracle() {
        val pkg = Converter.unpack(Parser().deserialize(generatedGoFile("regressions/usvm_regressions.json").path))
        val expected = pkg.methods.filter { it.parameters.isEmpty() && it.metName != "init" }.map { it.metName }.toSet()
        val oracle = Json.parseToJsonElement(generatedGoFile("native-oracle.json").readText()).jsonObject

        assertEquals(expected, oracle.keys)
    }

    private val sampleClasses = listOf(
        org.usvm.samples.algorithms.AlgorithmsTest::class.java,
        org.usvm.samples.algorithms.SlowAlgorithmsTest::class.java,
        org.usvm.samples.arithmetic.ArithmeticRegressionTest::class.java,
        org.usvm.samples.arrays.ArrayRegressionTest::class.java,
        org.usvm.samples.arrays.ArraysTest::class.java,
        org.usvm.samples.calls.CallsTest::class.java,
        org.usvm.samples.collections.maps.MapAllocationTest::class.java,
        org.usvm.samples.collections.maps.MapIterationTest::class.java,
        org.usvm.samples.collections.maps.MapLookupTest::class.java,
        org.usvm.samples.collections.maps.MapMutationTest::class.java,
        org.usvm.samples.collections.maps.MapRegressionTest::class.java,
        org.usvm.samples.collections.maps.SlowMapIterationTest::class.java,
        org.usvm.samples.collections.slices.SliceAlgorithmsTest::class.java,
        org.usvm.samples.collections.slices.SliceAllocationTest::class.java,
        org.usvm.samples.collections.slices.SliceMutationTest::class.java,
        org.usvm.samples.collections.slices.SliceRegressionTest::class.java,
        org.usvm.samples.collections.slices.SliceViewsTest::class.java,
        org.usvm.samples.collections.slices.SymbolicSliceAliasTest::class.java,
        org.usvm.samples.controlflow.ConditionsTest::class.java,
        org.usvm.samples.controlflow.LoopsTest::class.java,
        org.usvm.samples.controlflow.SlowLoopsTest::class.java,
        org.usvm.samples.controlflow.SymbolicBranchTest::class.java,
        org.usvm.samples.exceptions.DeferTest::class.java,
        org.usvm.samples.globals.GlobalsTest::class.java,
        org.usvm.samples.objects.ObjectsTest::class.java,
        org.usvm.samples.pointers.PointersTest::class.java,
        org.usvm.samples.strings.NamedStringsTest::class.java,
        org.usvm.samples.strings.StringConstraintsTest::class.java,
        org.usvm.samples.strings.StringRegressionTest::class.java,
        org.usvm.samples.strings.StringsTest::class.java,
        org.usvm.samples.types.InterfacesTest::class.java,
        org.usvm.samples.types.NamedTypesTest::class.java,
        org.usvm.samples.types.StructsTest::class.java,
        org.usvm.samples.unsupported.GoUnsupportedTest::class.java,
    )
}
