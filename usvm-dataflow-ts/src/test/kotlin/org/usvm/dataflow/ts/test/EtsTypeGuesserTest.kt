package org.usvm.dataflow.ts.test

import org.jacodb.ets.graph.EtsApplicationGraphImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsApplicationGraphWithExplicitEntryPoint
import org.usvm.dataflow.ts.infer.EtsMethodTypeFacts
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.test.utils.ExpectedTypesExtractor
import org.usvm.dataflow.ts.test.utils.MethodTypesFacts
import org.usvm.dataflow.ts.test.utils.autoLoadEtsFileFromResource
import org.usvm.dataflow.ts.util.EtsTraits
import kotlin.test.assertTrue

class EtsTypeGuesserTest {
    companion object {
        private fun load(name: String): EtsFile {
            return autoLoadEtsFileFromResource("/ts/$name.ts")
        }
    }

    @Test
    fun `use non unique fields`() {
        val file = load("guesser")

        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useNonUniqueField" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }

        val facts = manager.analyze(listOf(entrypoint), guessUniqueTypes = true)

        checkAnObjectTypeOfSingleArgument(facts) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls == null && typeFact.properties.keys.single() == "defaultA"
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.fromEtsMethodTypeFacts(facts.values.single())

        assertFalse(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true))
    }

    @Test
    fun `use unique fields`() {
        val file = load("guesser")

        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useUniqueFields" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }

        val facts = manager.analyze(listOf(entrypoint), guessUniqueTypes = true)

        checkAnObjectTypeOfSingleArgument(facts) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.fromEtsMethodTypeFacts(facts.values.single())

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true))
    }

    @Test
    fun `use unique and non unique fields`() {
        val file = load("guesser")

        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useBothA" }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }

        val facts = manager.analyze(listOf(entrypoint), guessUniqueTypes = true)

        checkAnObjectTypeOfSingleArgument(facts) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val actualTypes = MethodTypesFacts.fromEtsMethodTypeFacts(facts.values.single())

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true))
    }

    @Test
    fun `use unique methods`() {
        val file = load("guesser")

        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useUniqueMethods" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }

        val facts = manager.analyze(listOf(entrypoint), guessUniqueTypes = true)

        checkAnObjectTypeOfSingleArgument(facts) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "MethodsContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.fromEtsMethodTypeFacts(facts.values.single())

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true))
    }

    @Test
    fun `use non unique methods`() {
        val file = load("guesser")

        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useNotUniqueMethod" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }

        val facts = manager.analyze(listOf(entrypoint), guessUniqueTypes = true)

        checkAnObjectTypeOfSingleArgument(facts) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls == null && typeFact.properties.keys.single() == "notUniqueFunction"
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.fromEtsMethodTypeFacts(facts.values.single())

        assertFalse(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true))
    }

    @Test
    fun `use function and field`() {
        val file = load("guesser")

        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useFunctionAndField" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }

        val facts = manager.analyze(listOf(entrypoint), guessUniqueTypes = true)

        checkAnObjectTypeOfSingleArgument(facts) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.fromEtsMethodTypeFacts(facts.values.single())

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true))
    }

    private inline fun <reified T : EtsTypeFact> checkAnObjectTypeOfSingleArgument(
        facts: Map<EtsMethod, EtsMethodTypeFacts>,
        predicate: (T) -> Boolean
    ) {
        val fact = facts.values.single()
        val type = fact.types.filterNot { it.key is AccessPathBase.Return }.values.single() as T

        assertTrue(predicate(type))
    }
}