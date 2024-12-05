package org.usvm.dataflow.ts.test

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.test.utils.loadEtsFile
import org.jacodb.ets.test.utils.loadMultipleEtsFilesFromDirectory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.EtsApplicationGraphWithExplicitEntryPoint
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
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.test.assertTrue

class EtsTypeResolverTest {
    companion object {
        private fun load(name: String): EtsFile {
            return autoLoadEtsFileFromResource("/ts/$name.ts")
        }
    }

    private val yourPrefixForTestFolders = "C:/work/TestProjects"
    private val testProjectsVersion = "TestProjects_2024_11_14"
    private val pathToSDK: String = TODO("Put your path here")

    private fun loadEtsScene(paths: List<Path>): EtsScene {
        val files = paths.flatMap {  path ->
            check(path.exists()) { "Path does not exist: $path" }
            if (path.isRegularFile()) {
                val file = loadEtsFile(path)
                listOf(file)
            } else {
                loadMultipleEtsFilesFromDirectory(path).asIterable()
            }
        }
        return EtsScene(files)
    }


    @Test
    fun testTestHap() {
        val projectAbc = "$yourPrefixForTestFolders/$testProjectsVersion/CallUI"
        val abcScene = loadEtsScene(
            listOf(
                Paths.get(projectAbc),
                Paths.get(pathToSDK)
            )
        )
        val graphAbc = createApplicationGraph(abcScene)

        val entrypoint = EntryPointsProcessor.extractEntryPoints(abcScene) // TODO fix error with abc and ast methods

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graphAbc) // TODO replace with abc
        }

        val result = manager
            .analyze(entrypoint.mainMethods, entrypoint.allMethods)
            .withGuessedTypes(abcScene)
        // TODO replace with abc
        // TODO fix error with abc and ast methods

        val classMatcherStatistics = ClassMatcherStatistics()

        val inferredLocals = result.inferredTypes.flatMap { (m, es) -> es.mapNotNull { (b, f) -> if (b is AccessPathBase.Local) m to b.name else null } }
        val totalLocals = abcScene.classes.flatMap { it.methods }.flatMap { m -> m.locals.map { m to it.name } }

        val zeroLocals = totalLocals.toSet() - inferredLocals.toSet()
        val zeroLocalsFromConstructors = zeroLocals
            .filter { (m, n) -> n.startsWith("$") }
            .map { (m, n) ->
                Pair(m, n) to m.cfg.stmts.filter {
                    if (it !is EtsAssignStmt) return@filter false
                    val lhv = it.lhv
                    if (lhv !is EtsLocal) return@filter false
                    lhv.name == n
                }
            }

        saveTypeInferenceComparison(entrypoint.allMethods, entrypoint.allMethods, graphAbc, graphAbc, result, classMatcherStatistics, abcScene) // TODO fix error with abc and ast methods
        classMatcherStatistics.dumpStatistics("callkit.txt")
    }

    fun runOnProject(projectID: String, abcPath: String, astPath: String) {
        val projectAbc = "$yourPrefixForTestFolders/$testProjectsVersion/$abcPath"
        val abcScene = loadProjectFromJsons(projectAbc)

        val projectAst = "$yourPrefixForTestFolders/AST/$astPath"
        val astScene = loadProjectFromAst(projectAst)

        val graphAbc = createApplicationGraph(abcScene) as EtsApplicationGraphWithExplicitEntryPoint
        val graphAst = createApplicationGraph(astScene) as EtsApplicationGraphWithExplicitEntryPoint

        // TODO fix error with abc and ast methods
        val entrypoint = EntryPointsProcessor.extractEntryPoints(abcScene)
        val astMethods = extractAllAstMethods(astScene, abcScene)

        println(entrypoint.mainMethods.map { it.signature })
        println(entrypoint.allMethods.map { it.signature })

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graphAbc) // TODO replace with abc
        }

        // TODO replace graphAst with graphAbc ?
        val result = manager
            .analyze(entrypoint.mainMethods, entrypoint.allMethods.filter { it.isPublic })
            .withGuessedTypes(abcScene)

        val classMatcherStatistics = ClassMatcherStatistics()

        // TODO fix error with abc and ast methods
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

        println(graphAbc.stats)
        println("Resolved calls: ${graphAst.totalResolvedCalls.get()} / ${graphAst.run { totalResolvedCalls.get() + totalUnresolvedCalls.get() }}")
    }

    @Test
    fun testLoadProject1() = runOnProject(
        projectID = "project1",
        abcPath = "callui-default-signed",
        astPath = "16_CallUI/applications_call_230923_4de8"
    )

    @Test
    fun testLoadProject2() = runOnProject(
        projectID = "project2",
        abcPath = "CertificateManager_240801_843398b",
        astPath = "13_SecurityPrivacyCenter/security_privacy_center"
    )

    @Test
    fun testLoadProject3() = runOnProject(
        projectID = "project3",
        abcPath = "mobiledatasettings-callui-default-signed",
        astPath = "16_CallUI/applications_call_230923_4de8"
    )

    @Test
    fun testLoadProject4() = runOnProject(
        projectID = "project4",
        abcPath = "Music_Demo_240727_98a3500",
        astPath = "16_CallUI/applications_call_230923_4de8"
    )

    @Test
    fun testLoadProject5() = runOnProject(
        projectID = "project5",
        abcPath = "phone_photos",
        astPath = "15_Photos/applications_photos_240905_ea8d1"
    )

    @Test
    fun testLoadProject6() = runOnProject(
        projectID = "project6",
        abcPath = "phone-default-signed_20240409_144519",
        astPath = "17_Camera/applications_camera_240409_1da805f8"
    )

    @Test
    fun testLoadProject7() = runOnProject(
        projectID = "project7",
        abcPath = "SecurityPrivacyCenter_240801_843998b",
        astPath = "13_SecurityPrivacyCenter/security_privacy_center"
    )

    @Test
    fun `use non unique fields`() {
        val file = load("resolver_test")

        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoint = project.classes
            .flatMap { it.methods }
            .single { it.name == "useNonUniqueField" }

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(project)

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

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(project)

        checkAnObjectTypeOfSingleArgument(result.inferredTypes[entrypoint]!!) { typeFact: EtsTypeFact.ObjectEtsTypeFact ->
            typeFact.cls?.typeName == "FieldContainerToInfer" && typeFact.properties.isEmpty()
        }

        val expectedTypes = ExpectedTypesExtractor(graph).extractTypes(entrypoint)
        val actualTypes = MethodTypesFacts.from(result, entrypoint)

        assertTrue(expectedTypes.matchesWithTypeFacts(actualTypes, ignoreReturnType = true, project))
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

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(project)

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

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(project)

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

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(project)

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

        val manager = with(EtsTraits()) {
            TypeInferenceManager(graph)
        }

        val result = manager.analyze(listOf(entrypoint)).withGuessedTypes(project)

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
        isOnlyPublic: Boolean = true,
    ): List<EtsMethod> {
        val astMethods = astScene.classes.flatMapTo(mutableSetOf()) {
            it.methods.map { method -> method.name to method.enclosingClass.name }
        }

        return abcScene.classes
            .asSequence()
            .filterNot { it.name.startsWith("AnonymousClass-") }
            .flatMap { it.methods }
            .filter { it.isPublic }
            .filter {
                !filterByAst || astMethods.any { method ->
                    it.name == method.first && it.enclosingClass.name == method.second
                }
            }
            .filter { !isOnlyPublic || it.isPublic }
            .toList()
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
            val expectedTypes = ExpectedTypesExtractor(graphAst).extractTypes(m)
            val abcMethod = abcMethods.singleOrNull {
                it.name == m.name && it.enclosingClass.name == m.enclosingClass.name
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
