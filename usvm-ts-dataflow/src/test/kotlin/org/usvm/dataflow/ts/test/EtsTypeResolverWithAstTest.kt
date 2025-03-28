package org.usvm.dataflow.ts.test

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.dataflow.ts.getResourcePath
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.test.utils.ClassMatcherStatistics
import org.usvm.dataflow.ts.test.utils.ExpectedTypesExtractor
import org.usvm.dataflow.ts.test.utils.MethodTypesFacts
import org.usvm.dataflow.ts.test.utils.TypeInferenceStatistics
import org.usvm.dataflow.ts.util.EtsTraits
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.assertTrue

@EnabledIf("projectAvailable")
class EtsTypeResolverWithAstTest {

    companion object {
        private val yourPrefixForTestFolders = "C:/work/TestProjects"
        private val testProjectsVersion = "TestProjects_2024_12_5"
        private val pathToSDK: String? = null // TODO: Put your path here

        @JvmStatic
        private fun projectAvailable(): Boolean {
            return Path(yourPrefixForTestFolders).exists()
        }

        private fun load(name: String): EtsFile {
            return loadEtsFileAutoConvert(getResourcePath("/ts/$name.ts"))
        }
    }

    fun runOnProjectWithAstComparison(projectID: String, abcPath: String, astPath: String) {
        val projectAbc = "$yourPrefixForTestFolders/$testProjectsVersion/$abcPath"
        val abcScene = loadEtsProjectFromIR(Path(projectAbc), pathToSDK?.let { Path(it) })

        val projectAst = "$yourPrefixForTestFolders/AST/$astPath"
        val astScene = loadEtsProjectAutoConvert(Path(projectAst))

        val graphAbc = createApplicationGraph(abcScene)
        val graphAst = createApplicationGraph(astScene)

        val entrypoint = EntryPointsProcessor(abcScene).extractEntryPoints()
        val astMethods = extractAllAstMethods(astScene, abcScene)

        println(entrypoint.mainMethods.map { it.signature })
        println(entrypoint.allMethods.map { it.signature })

        val guesser = TypeGuesser(abcScene)

        val manager = TypeInferenceManager(EtsTraits(), graphAbc)
        val resultBasic = manager.analyze(
            entrypoints = entrypoint.mainMethods,
            allMethods = entrypoint.allMethods.filter { it.isPublic },
        )
        val result = resultBasic.withGuessedTypes(guesser)

        val classMatcherStatistics = ClassMatcherStatistics()
        saveTypeInferenceComparison(
            astMethods,
            entrypoint.allMethods,
            graphAst,
            graphAbc,
            result,
            classMatcherStatistics,
            abcScene
        )
        classMatcherStatistics.dumpStatistics("$projectID.txt")

        val sceneStatistics = TypeInferenceStatistics()
        abcScene.projectAndSdkClasses
            .flatMap { it.methods }
            .forEach {
                val facts = MethodTypesFacts.from(result, it)
                sceneStatistics.compareSingleMethodFactsWithTypesInScene(facts, it, graphAbc)
            }
        sceneStatistics.dumpStatistics("${projectID}_scene_comparison.txt")
    }

    @Test
    fun testLoadProject1() = runOnProjectWithAstComparison(
        projectID = "project1",
        abcPath = "callui-default-signed",
        astPath = "16_CallUI/applications_call_230923_4de8"
    )

    @Test
    fun testLoadProject2() = runOnProjectWithAstComparison(
        projectID = "project2",
        abcPath = "CertificateManager_240801_843398b",
        astPath = "13_SecurityPrivacyCenter/security_privacy_center"
    )

    @Test
    fun testLoadProject3() = runOnProjectWithAstComparison(
        projectID = "project3",
        abcPath = "mobiledatasettings-callui-default-signed",
        astPath = "16_CallUI/applications_call_230923_4de8"
    )

    @Test
    fun testLoadProject4() = runOnProjectWithAstComparison(
        projectID = "project4",
        abcPath = "Music_Demo_240727_98a3500",
        astPath = "16_CallUI/applications_call_230923_4de8"
    )

    @Test
    fun testLoadProject5() = runOnProjectWithAstComparison(
        projectID = "project5",
        abcPath = "phone_photos-default-signed_20240905_151755",
        astPath = "15_Photos/applications_photos_240905_ea8d1"
    )

    @Test
    fun testLoadProject6() = runOnProjectWithAstComparison(
        projectID = "project6",
        abcPath = "phone-default-signed_20240409_144519",
        astPath = "17_Camera/applications_camera_240409_1da805f8"
    )

    @Test
    fun testLoadProject7() = runOnProjectWithAstComparison(
        projectID = "project7",
        abcPath = "SecurityPrivacyCenter_240801_843998b",
        astPath = "13_SecurityPrivacyCenter/security_privacy_center"
    )

    @Test
    fun `use non unique fields`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val entrypoint = project.projectClasses
            .flatMap { it.methods }
            .single { it.name == "useNonUniqueField" }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val resultBasic = manager.analyze(listOf(entrypoint))
        val result = resultBasic.withGuessedTypes(guesser)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls == null && typeFact.properties.keys.single() == "defaultA"
        }

        val expectedTypes = ExpectedTypesExtractor(project).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertFalse(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use unique fields`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val entrypoint = project.projectClasses
            .flatMap { it.methods }
            .single { it.name == "useUniqueFields" }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val resultBasic = manager.analyze(listOf(entrypoint))
        val result = resultBasic.withGuessedTypes(guesser)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { fact: EtsTypeFact.ObjectEtsTypeFact ->
            fact.cls?.typeName == "FieldContainerToInfer" && fact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(project).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use unique and non unique fields`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val entrypoint = project.projectClasses
            .flatMap { it.methods }
            .single { it.name == "useBothA" }

        val expectedTypes = ExpectedTypesExtractor(project).extractTypes(entrypoint)

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val resultBasic = manager.analyze(listOf(entrypoint))
        val result = resultBasic.withGuessedTypes(guesser)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { fact: EtsTypeFact.ObjectEtsTypeFact ->
            fact.cls?.typeName == "FieldContainerToInfer" && fact.properties.isEmpty()
        }

        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use unique methods`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val entrypoint = project.projectClasses
            .flatMap { it.methods }
            .single { it.name == "useUniqueMethods" }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val resultBasic = manager.analyze(listOf(entrypoint))
        val result = resultBasic.withGuessedTypes(guesser)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "MethodsContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(project).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use non unique methods`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val entrypoint = project.projectClasses
            .flatMap { it.methods }
            .single { it.name == "useNotUniqueMethod" }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val resultBasic = manager.analyze(listOf(entrypoint))
        val result = resultBasic.withGuessedTypes(guesser)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls == null && typeFact.properties.keys.single() == "notUniqueFunction"
        }

        val expectedTypes = ExpectedTypesExtractor(project).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertFalse(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use function and field`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val entrypoint = project.projectClasses
            .flatMap { it.methods }
            .single { it.name == "useFunctionAndField" }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val resultBasic = manager.analyze(listOf(entrypoint))
        val result = resultBasic.withGuessedTypes(guesser)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(project).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    private fun extractAllAstMethods(astScene: EtsScene, abcScene: EtsScene): List<EtsMethod> {
        val abcMethods = abcScene.projectAndSdkClasses
            .flatMapTo(hashSetOf()) {
                it.methods.map { method -> method.name }
            }
        return astScene.projectAndSdkClasses
            .flatMap { it.methods }
            .filter { it.name in abcMethods }
    }

    private fun saveTypeInferenceComparison(
        astMethods: List<EtsMethod>,
        abcMethods: List<EtsMethod>,
        graphAst: EtsApplicationGraph,
        graphAbc: EtsApplicationGraph,
        result: TypeInferenceResult,
        classMatcherStatistics: ClassMatcherStatistics,
        abcScene: EtsScene,
    ) {
        astMethods.forEach { m ->
            val expectedTypes = ExpectedTypesExtractor(graphAst.cp).extractTypes(m)
            val abcMethod = abcMethods.singleOrNull {
                it.name == m.name && it.signature.enclosingClass.name == m.signature.enclosingClass.name
            } ?: return@forEach
            val actualTypes = MethodTypesFacts.from(result, m)
            classMatcherStatistics.calculateStats(
                actualTypes,
                expectedTypes,
                abcScene,
                m,
                abcMethod,
                graphAst,
                graphAbc
            )
        }
    }

    private inline fun <reified T : EtsTypeFact> checkAnObjectTypeOfSingleArgument(
        types: Map<AccessPathBase, EtsTypeFact>,
        predicate: (T) -> Boolean,
    ) {
        val type = types.filterKeys { it is AccessPathBase.Arg }.values.single() as T
        assertTrue(predicate(type))
    }
}
