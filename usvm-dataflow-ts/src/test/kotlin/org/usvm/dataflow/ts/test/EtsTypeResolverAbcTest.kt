package org.usvm.dataflow.ts.test

import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.EtsApplicationGraphWithExplicitEntryPoint
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.util.EtsTraits
import org.usvm.dataflow.ts.util.MethodTypesFacts
import org.usvm.dataflow.ts.util.TypeInferenceStatistics
import kotlin.io.path.Path
import kotlin.io.path.exists

@EnabledIf("projectAvailable")
class EtsTypeResolverAbcTest {

    companion object {
        private val yourPrefixForTestFolders = "C:/work/TestProjects"
        private val testProjectsVersion = "TestProjects_2024_12_5"
        private val pathToSDK: String? = null // TODO: Put your path here

        @JvmStatic
        private fun projectAvailable(): Boolean {
            return Path(yourPrefixForTestFolders).exists()
        }
    }

    private fun runOnAbcProject(projectID: String, abcPath: String) {
        val projectAbc = "$yourPrefixForTestFolders/$testProjectsVersion/$abcPath"

        val abcScene = loadEtsProjectFromIR(Path(projectAbc), pathToSDK?.let { Path(it) })
        val graphAbc = createApplicationGraph(abcScene) as EtsApplicationGraphWithExplicitEntryPoint
        val guesser = TypeGuesser(abcScene)

        val entrypoint = EntryPointsProcessor.extractEntryPoints(abcScene)
        val allMethods = entrypoint.allMethods.filter { it.isPublic }.filter { it.cfg.stmts.isNotEmpty() }

        val manager = TypeInferenceManager(EtsTraits(), graphAbc)
        val result = manager
            .analyze(entrypoint.mainMethods, allMethods)
            .withGuessedTypes(guesser)

        val sceneStatistics = TypeInferenceStatistics()
        entrypoint.allMethods
            .filter { it.cfg.stmts.isNotEmpty() }
            .forEach {
                val methodTypeFacts = MethodTypesFacts.from(result, it)
                sceneStatistics.compareSingleMethodFactsWithTypesInScene(methodTypeFacts, it, graphAbc)
            }
        sceneStatistics.dumpStatistics("${projectID}_scene_comparison.txt")
    }

    @Test
    fun testLoadAbcProject1() = runOnAbcProject(
        projectID = "project1",
        abcPath = "callui-default-signed",
    )

    @Test
    fun testLoadAbcProject2() = runOnAbcProject(
        projectID = "project2",
        abcPath = "CertificateManager_240801_843398b",
    )

    @Test
    fun testLoadAbcProject3() = runOnAbcProject(
        projectID = "project3",
        abcPath = "mobiledatasettings-callui-default-signed",
    )

    @Test
    fun testLoadAbcProject4() = runOnAbcProject(
        projectID = "project4",
        abcPath = "Music_Demo_240727_98a3500",
    )

    @Test
    fun testLoadAbcProject5() = runOnAbcProject(
        projectID = "project5",
        abcPath = "phone_photos-default-signed_20240905_151755",
    )

    @Test
    fun testLoadAbcProject6() = runOnAbcProject(
        projectID = "project6",
        abcPath = "phone-default-signed_20240409_144519",
    )

    @Test
    fun testLoadAbcProject7() = runOnAbcProject(
        projectID = "project7",
        abcPath = "SecurityPrivacyCenter_240801_843998b",
    )
}
