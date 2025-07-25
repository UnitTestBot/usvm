package org.usvm.project

import mu.KotlinLogging
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.dataflow.ts.testFactory
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath
import org.usvm.util.getResourcePathOrNull
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@EnabledIf("projectsAvailable")
class RunOnAllProjects {
    companion object {
        private const val PROJECTS_ROOT = "/projects"
        private const val SDK_TS_PATH = "/sdk/typescript"
        private const val SDK_OHOS_PATH = "/sdk/ohos/5.0.1.111/ets"

        @JvmStatic
        private fun projectsAvailable(): Boolean {
            return getResourcePathOrNull(PROJECTS_ROOT) != null
        }

        val machineOptions: UMachineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
            exceptionsPropagation = true,
            timeout = 1000000000.seconds,
            stepsFromLastCovered = 3500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
            typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
        )
    }

    private fun createScene(projectName: String): EtsScene {
        val projectPath = "$PROJECTS_ROOT/$projectName/source/entry"
        val project = loadEtsProjectAutoConvert(getResourcePath(projectPath))
        val sdkFiles = listOf(SDK_TS_PATH, SDK_OHOS_PATH).flatMap { sdk ->
            val sdkPath = getResourcePath(sdk)
            val sdkProject = loadEtsProjectAutoConvert(sdkPath, useArkAnalyzerTypeInference = null)
            sdkProject.projectFiles
        }
        return EtsScene(project.projectFiles, sdkFiles, projectName = project.projectName)
    }

    @TestFactory
    fun dynamicTestsForAllProjects() = testFactory {
        val projects = getResourcePath(PROJECTS_ROOT)
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { it.name }
        logger.info { "Found ${projects.size} projects: ${projects.joinToString(", ")}" }

        for (projectName in projects) {
            logger.info { "Processing project: $projectName" }

            test("Run on each class for $projectName") {
                logger.info { "Running tests on each class in project: $projectName" }
                val scene = createScene(projectName)
                val exceptions = mutableListOf<Throwable>()
                val classes = scene.projectClasses
                    .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
                logger.info { "Running on ${classes.size} classes for project $projectName" }

                for (cls in classes) {
                    val methods = cls.methods
                        .filterNot { it.cfg.stmts.isEmpty() }
                        .filterNot { it.isStatic }
                        .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                        .filterNot { it.name == "build" }
                        .filterNot { it.name == INSTANCE_INIT_METHOD_NAME }
                        .filterNot { it.name == STATIC_INIT_METHOD_NAME }
                        .filterNot { it.name == CONSTRUCTOR_NAME }
                    if (methods.isEmpty()) continue
                    logger.info { "Running on class ${cls.name} with ${methods.size} methods" }

                    runCatching {
                        val tsOptions = TsOptions()
                        TsMachine(scene, machineOptions, tsOptions).use { machine ->
                            val states = machine.analyze(methods)
                            states.let {}
                        }
                    }.onFailure {
                        exceptions += it
                    }
                }

                val exc = exceptions.groupBy { it }
                logger.info { "Total exceptions: ${exc.size}" }
                for (es in exc.values.sortedBy { it.size }.asReversed()) {
                    logger.info { "${es.first()}" }
                }
            }

            test("Run on all methods for $projectName") {
                logger.info { "Running tests on all methods in project: $projectName" }
                val scene = createScene(projectName)
                val methods = scene.projectClasses
                    .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
                    .flatMap { cls ->
                        cls.methods
                            .filterNot { it.cfg.stmts.isEmpty() }
                            .filterNot { it.isStatic }
                            .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                            .filterNot { it.name == "build" }
                    }
                logger.info { "Running on ${methods.size} methods for project $projectName" }

                val tsOptions = TsOptions()
                TsMachine(scene, machineOptions, tsOptions).use { machine ->
                    val states = machine.analyze(methods)
                    states.let {}
                }
            }
        }
    }
}
