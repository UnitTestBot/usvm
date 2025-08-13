package org.usvm.project

import mu.KotlinLogging
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.dataflow.ts.TestNodeBuilder
import org.usvm.dataflow.ts.containerForEach
import org.usvm.dataflow.ts.testFactory
import org.usvm.dataflow.ts.testForEach
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@Tag("manual")
class ProjectRunner {
    companion object {
        private const val PROJECTS_ROOT = "/projects"

        private const val SDK_ADHOC_PATH = "/sdk/adhoc"

        /**
         * ## Instructions for getting TypeScript SDK:
         *
         * 1. Download the TypeScript SDK from one of the following sources:
         *
         *    - Option 1: Download from npm (recommended)
         *      ```sh
         *      npm install typescript@latest
         *      ```
         *
         *    - Option 2: Download from GitHub releases: https://github.com/microsoft/TypeScript/releases/latest
         *
         * 2. Extract the TypeScript library files (*.d.ts files) from:
         *    - `node_modules/typescript/lib/` (if using npm)
         *    - `TypeScript-<version>/lib/` (if using GitHub releases)
         *
         * 3. Place the TypeScript SDK into resources as follows:
         *    ```
         *    src/
         *      test/
         *        resources/
         *          sdk/
         *            typescript/
         *              lib.d.ts
         *              lib.es2015.d.ts
         *              lib.es2020.d.ts
         *              lib.dom.d.ts
         *              ... (other TypeScript lib files)
         *    ```
         */
        private const val SDK_TYPESCRIPT_PATH = "/sdk/typescript"

        /**
         * ## Instructions for getting OpenHarmony SDK:
         *
         * 1. Visit https://repo.huaweicloud.com/harmonyos/os/
         *
         * 2. Download the latest version (e.g., `5.0.3`):
         *
         *    ```sh
         *    curl -OL https://repo.huaweicloud.com/openharmony/os/5.0.3-Release/ohos-sdk-windows_linux-public.tar.gz
         *    ```
         *
         * 3. Extract the archive and find the folder `ets` with sub-folders `api`, `arkts`, `component`, `kits`.
         *    _Everything else can be thrown away._
         *
         * 4. Place the SDK into resources as follows:
         *    ```
         *    src/
         *      test/
         *        resources/
         *          sdk/
         *            ohos/
         *              <version>/  (e.g., `5.0.1.111`)
         *                ets/
         *                  api/
         *                  arkts/
         *                  component/
         *                  kits/
         *    ```
         *
         * 5. Update the `SDK_OHOS_PATH` const to point to the correct version.
         */
        private const val SDK_OHOS_PATH = "/sdk/ohos/5.0.1.111/ets"

        val machineOptions: UMachineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
            timeout = 10.seconds,
            stepsFromLastCovered = 3500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
            typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
        )
    }

    private val sdkFiles: List<EtsFile> by lazy {
        listOf(SDK_ADHOC_PATH, SDK_TYPESCRIPT_PATH, SDK_OHOS_PATH).flatMap { sdk ->
            logger.info { "Loading SDK from path: $sdk" }
            val sdkPath = getResourcePath(sdk)
            val sdkProject = loadEtsProjectAutoConvert(sdkPath, useArkAnalyzerTypeInference = null)
            sdkProject.projectFiles
        }.also {
            logger.info { "Loaded total ${it.size} SDK files" }
        }
    }

    private fun createScene(projectName: String): EtsScene {
        logger.info { "Creating scene for project: $projectName" }
        val projectPath = "$PROJECTS_ROOT/$projectName/source"
        logger.info { "Loading project from path: $projectPath" }
        val project = loadEtsProjectAutoConvert(getResourcePath(projectPath))
        logger.info { "Loaded project $projectName with ${project.projectFiles.size} files" }
        return EtsScene(project.projectFiles, sdkFiles, projectName = projectName)
    }

    private fun runMachineOnClass(scene: EtsScene, cls: EtsClass) {
        logger.info { "Running on class $cls in project ${scene.projectName}" }
        val methods = cls.methods
            .filterNot { it.cfg.stmts.isEmpty() }
            .filterNot { it.isStatic }
            .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
            .filterNot { it.name == "build" }
            .filterNot { it.name == INSTANCE_INIT_METHOD_NAME }
            .filterNot { it.name == STATIC_INIT_METHOD_NAME }
            .filterNot { it.name == CONSTRUCTOR_NAME }
        if (methods.isEmpty()) return
        logger.info { "Running on ${methods.size} methods in class $cls" }

        val tsOptions = TsOptions()
        TsMachine(scene, machineOptions, tsOptions).use { machine ->
            val states = machine.analyze(methods)
            states.let {}
        }
    }

    private fun TestNodeBuilder.testOnEachClass(scene: EtsScene) {
        container("Run on each class in ${scene.projectName}") {
            logger.info { "Running on each class in project ${scene.projectName}" }
            val classes = scene.projectClasses
                .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
            logger.info { "Running on ${classes.size} classes in project ${scene.projectName}" }

            val exceptions = mutableListOf<Throwable>()

            testForEach(
                classes, // .take(3)
                { "Run on class ${it.name} @${it.signature.file.fileName}" }
            ) { cls ->
                try {
                    runMachineOnClass(scene, cls)
                } catch (e: Throwable) {
                    exceptions += e
                }
            }

            test("@afterAll") {
                val exc = exceptions.groupBy { it }
                logger.info { "Total exceptions: ${exc.size}" }
                for (es in exc.values.sortedBy { it.size }.asReversed()) {
                    logger.info { "${es.first()}" }
                }
                assertTrue(exc.isEmpty(), "There are exceptions!")
            }
        }
    }

    private fun runMachineOnAllMethods(scene: EtsScene) {
        logger.info { "Running on all methods in project ${scene.projectName}" }
        val methods = scene.projectClasses
            .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
            .flatMap { cls ->
                cls.methods
                    .filterNot { it.cfg.stmts.isEmpty() }
                    .filterNot { it.isStatic }
                    .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                    .filterNot { it.name == "build" }
            }
        logger.info { "Running on ${methods.size} methods in project ${scene.projectName}" }

        val tsOptions = TsOptions()
        TsMachine(scene, machineOptions, tsOptions).use { machine ->
            val states = machine.analyze(methods)
            states.let {}
        }
    }

    private fun TestNodeBuilder.testOnAllMethods(scene: EtsScene) {
        test("Run on all methods in ${scene.projectName}") {
            runMachineOnAllMethods(scene)
        }
    }

    @TestFactory
    fun dynamicTestsForAllProjects() = testFactory {
        val projects = getResourcePath(PROJECTS_ROOT)
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { it.name }
        logger.info { "Found ${projects.size} projects: ${projects.joinToString(", ")}" }

        containerForEach(
            projects.take(3),
            { "Project $it" }
        ) { projectName ->
            logger.info { "Processing project: $projectName" }
            val scene = createScene(projectName)

            testOnEachClass(scene)

            testOnAllMethods(scene)
        }
    }

    private val particularProjectName: String = run {
        // "Demo_Calc"
        "Demo_Photos"
    }

    @TestFactory
    fun `run on each class in a particular project`() = testFactory {
        logger.info { "Processing project: $particularProjectName" }
        val scene = createScene(particularProjectName)

        testOnEachClass(scene)
    }

    @Test
    fun `run on a particular class in a particular project`() {
        val scene = createScene(particularProjectName)
        val cls = scene.projectClasses.firstOrNull { it.toString() == "@source/entry/pages/VideoBrowser.ets: VideoBrowser" }
            ?: error("Class not found in project $particularProjectName")
        runMachineOnClass(scene, cls)
    }

    @TestFactory
    fun `run on all methods in a particular project`() = testFactory {
        logger.info { "Processing project: $particularProjectName" }
        val scene = createScene(particularProjectName)

        testOnAllMethods(scene)
    }
}
