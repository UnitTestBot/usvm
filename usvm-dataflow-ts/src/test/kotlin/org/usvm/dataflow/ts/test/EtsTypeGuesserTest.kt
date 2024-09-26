package org.usvm.dataflow.ts.test

import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.test.utils.ClassMatcherStatistics
import org.usvm.dataflow.ts.test.utils.ExpectedTypesExtractor
import org.usvm.dataflow.ts.test.utils.MethodTypesFacts
import org.usvm.dataflow.ts.test.utils.autoLoadEtsFileFromResource
import org.usvm.dataflow.ts.test.utils.loadProjectFromAst
import org.usvm.dataflow.ts.test.utils.loadProjectFromJsons
import org.usvm.dataflow.ts.util.EtsTraits
import kotlin.test.assertTrue

class EtsTypeResolverTest {
    companion object {
        private fun load(name: String): EtsFile {
            return autoLoadEtsFileFromResource("/ts/$name.ts")
        }
    }

    private val yourPrefixForTestFolders = "YOUR_PREFIX"

    @Test
    fun testLoadProject1() {
        val projectAbc = "$yourPrefixForTestFolders/TestProjects/callui-default-signed"
        val abcScene = loadProjectFromJsons(projectAbc)

        val projectAst = "$yourPrefixForTestFolders/AST/16_CallUI/applications_call_230923_4de8"
        val astScene = loadProjectFromAst(projectAst)

        val graphAbc = createApplicationGraph(abcScene)
        val graphAst = createApplicationGraph(astScene)

        val entrypoint = extractEntryPoints(abcScene, astScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphAbc)
        }

        val result = manager.analyze(entrypoint).withGuessedTypes(graphAbc)

        val classMatcherStatistics = ClassMatcherStatistics()

        saveTypeInferenceComparison(astMethods, graphAst, result, classMatcherStatistics, abcScene)
        classMatcherStatistics.dumpStatistics("project1.txt")
    }

    @Test
    fun testLoadProject2() {
        val projectAbc = "$yourPrefixForTestFolders/TestProjects/CertificateManager_240801_843398b"
        val abcScene = loadProjectFromJsons(projectAbc)
        val graphAbc = createApplicationGraph(abcScene)

        val projectAst = "$yourPrefixForTestFolders/AST/13_SecurityPrivacyCenter/security_privacy_center"
        val astScene = loadProjectFromAst(projectAst)
        val graphAst = createApplicationGraph(astScene)

        val entrypoint = extractEntryPoints(abcScene, astScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphAbc)
        }

        val result = manager.analyze(entrypoint).withGuessedTypes(graphAbc)

        val classMatcherStatistics = ClassMatcherStatistics()

        saveTypeInferenceComparison(astMethods, graphAst, result, classMatcherStatistics, abcScene)
        classMatcherStatistics.dumpStatistics("project2.txt")
    }

    @Test
    fun testLoadProject3() {
        val projectAbc = "$yourPrefixForTestFolders/TestProjects/mobiledatasettings-callui-default-signed"
        val abcScene = loadProjectFromJsons(projectAbc)
        val graphAbc = createApplicationGraph(abcScene)

        val projectAst = "$yourPrefixForTestFolders/AST/16_CallUI/applications_call_230923_4de8"
        val astScene = loadProjectFromAst(projectAst)
        val graphAst = createApplicationGraph(astScene)

        val entrypoint = extractEntryPoints(abcScene, astScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphAbc)
        }

        val result = manager.analyze(entrypoint).withGuessedTypes(graphAbc)

        val classMatcherStatistics = ClassMatcherStatistics()

        saveTypeInferenceComparison(astMethods, graphAst, result, classMatcherStatistics, abcScene)
        classMatcherStatistics.dumpStatistics("project3.txt")
    }

    @Test
    fun testLoadProject4() {
        val projectAbc = "$yourPrefixForTestFolders/TestProjects/Music_Demo_240727_98a3500"
        val abcScene = loadProjectFromJsons(projectAbc)
        val graphAbc = createApplicationGraph(abcScene)

        val projectAst = "$yourPrefixForTestFolders/AST/12_Music_Demo/ArkTSDistributedMusicPlayer"
        val astScene = loadProjectFromAst(projectAst)
        val graphAst = createApplicationGraph(astScene)

        val entrypoints = extractEntryPoints(abcScene, astScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphAbc)
        }

        val result = manager.analyze(entrypoints).withGuessedTypes(graphAbc)

        val classMatcherStatistics = ClassMatcherStatistics()

        saveTypeInferenceComparison(astMethods, graphAst, result, classMatcherStatistics, abcScene)

        classMatcherStatistics.dumpStatistics("project4.txt")
    }

    @Test
    fun testLoadProject5() {
        val projectAbc = "$yourPrefixForTestFolders/TestProjects/phone_photos-default-signed_20240905_151755"
        val abcScene = loadProjectFromJsons(projectAbc)
        val graphAbc = createApplicationGraph(abcScene)

        val projectAst = "$yourPrefixForTestFolders/AST/15_Photos/applications_photos_240905_ea8d1"
        val astScene = loadProjectFromAst(projectAst)
        val graphAst = createApplicationGraph(astScene)

        val entrypoint = extractEntryPoints(abcScene, astScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphAbc)
        }

        val result = manager.analyze(entrypoint).withGuessedTypes(graphAbc)

        val classMatcherStatistics = ClassMatcherStatistics()

        saveTypeInferenceComparison(astMethods, graphAst, result, classMatcherStatistics, abcScene)
        classMatcherStatistics.dumpStatistics("project5.txt")
    }

    @Test
    fun testLoadProject6() {
        val projectAbc = "$yourPrefixForTestFolders/TestProjects/phone-default-signed_20240409_144519"
        val abcScene = loadProjectFromJsons(projectAbc)
        val graphAbc = createApplicationGraph(abcScene)

        val projectAst = "$yourPrefixForTestFolders/AST/17_Camera/applications_camera_240409_1da805f8"
        val astScene = loadProjectFromAst(projectAst)
        val graphAst = createApplicationGraph(astScene)

        val entrypoint = extractEntryPoints(abcScene, astScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphAbc)
        }

        val result = manager.analyze(entrypoint).withGuessedTypes(graphAbc)

        val classMatcherStatistics = ClassMatcherStatistics()

        saveTypeInferenceComparison(astMethods, graphAst, result, classMatcherStatistics, abcScene)
        classMatcherStatistics.dumpStatistics("project6.txt")
    }

    @Test
    fun testLoadProject7() {
        val projectAbc = "$yourPrefixForTestFolders/TestProjects/SecurityPrivacyCenter_240801_843998b"
        val abcScene = loadProjectFromJsons(projectAbc)
        val graphAbc = createApplicationGraph(abcScene)

        val projectAst = "$yourPrefixForTestFolders/AST/13_SecurityPrivacyCenter/security_privacy_center"
        val astScene = loadProjectFromAst(projectAst)
        val graphAst = createApplicationGraph(astScene)

        val entrypoint = extractEntryPoints(abcScene, astScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphAbc)
        }

        val result = manager.analyze(entrypoint).withGuessedTypes(graphAbc)

        val classMatcherStatistics = ClassMatcherStatistics()

        saveTypeInferenceComparison(astMethods, graphAst, result, classMatcherStatistics, abcScene)
        classMatcherStatistics.dumpStatistics("project7.txt")
    }

    @Test
    fun `use non unique fields`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useNonUniqueField" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(graph)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls == null && typeFact.properties.keys.single() == "defaultA"
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertFalse(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use unique fields`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useUniqueFields" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(graph)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))

        val classMatcherStatistics = ClassMatcherStatistics()
        classMatcherStatistics.verify(actualTypes, expectedTypes, project, entrypoint)
        println(classMatcherStatistics)
    }

    @Test
    fun `use unique and non unique fields`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useBothA" }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(graph)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use unique methods`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useUniqueMethods" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(graph)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "MethodsContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use non unique methods`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useNotUniqueMethod" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(graph)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls == null && typeFact.properties.keys.single() == "notUniqueFunction"
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertFalse(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    @Test
    fun `use function and field`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useFunctionAndField" }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(graph)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
    }

    private fun extractAllAstMethods(astScene: EtsScene, abcScene: EtsScene): List<EtsMethod> {
        val abcMethods = abcScene.classes.flatMap { it.methods.map { method -> method.name } }
        return astScene.classes.flatMap { it.methods }.filter { it.name in abcMethods }
    }

    private fun extractEntryPoints(
        abcScene: EtsScene,
        astScene: EtsScene,
        filterByAst: Boolean = true,
        isOnlyPublic: Boolean = true
    ): List<EtsMethod> {
        val astMethods = astScene.classes.flatMapTo(mutableSetOf()) { it.methods.map { method -> method.name } }

        return abcScene.classes
            .asSequence()
            .filterNot { it.name.startsWith("AnonymousClass-") }
            .flatMap { it.methods }
            .filter { it.isPublic }
            .filter { !filterByAst || it.name in astMethods }
            .filter { !isOnlyPublic || it.isPublic }
            .toList()
    }

    private fun saveTypeInferenceComparison(
        methodsToFindFactsFor: List<EtsMethod>,
        graphAst: EtsApplicationGraph,
        result: TypeInferenceResult,
        classMatcherStatistics: ClassMatcherStatistics,
        abcScene: EtsScene,
    ) {
        methodsToFindFactsFor.forEach { m ->
            val inferredReturnType = result.inferredReturnType.entries.firstOrNull {
                it.key.let { method -> method.name == m.name && method.enclosingClass.name == m.enclosingClass.name }

            }?.value
            val combinedThisFact = result.inferredCombinedThisType.entries.firstOrNull {
                it.key.name == m.enclosingClass.name
            }?.value

            val factsForMethod = result.inferredTypes.entries.singleOrNull {
                // TODO hack because of signatures
                it.key.let { method -> method.name == m.name && method.enclosingClass.name == m.enclosingClass.name }
            }
                ?.value
                ?: run {
                    if (inferredReturnType == null && combinedThisFact == null) {
                        classMatcherStatistics.saveAbsentResult(m)
                    }
                    return@forEach
                }

            val expectedTypes = ExpectedTypesExtractor(graphAst).extractTypes(m)
            val actualTypes = MethodTypesFacts.from(
                factsForMethod,
                inferredReturnType,
                combinedThisFact,
                m
            )
            classMatcherStatistics.verify(actualTypes, expectedTypes, abcScene, m)
        }
    }

    private inline fun <reified T : EtsTypeFact> checkAnObjectTypeOfSingleArgument(
        types: Map<AccessPathBase, EtsTypeFact>,
        predicate: (T) -> Boolean,
    ) {
        val type = types.values.single() as T
        assertTrue(predicate(type))
    }
}
