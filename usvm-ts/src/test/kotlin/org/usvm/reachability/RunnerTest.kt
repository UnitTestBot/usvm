package org.usvm.reachability

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Tag
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.reachability.TargetTrace
import org.usvm.api.reachability.TsReachabilityObserver
import org.usvm.api.reachability.createTargetsFromTraces
import org.usvm.api.reachability.dto.loadTraces
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.countLeaves
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Tag("manual")
class RunnerTest {

    //! -----
    //! Note: "current directory" is "usvm-ts" module
    //! -----

    private var options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        exceptionsPropagation = true,
        stopOnTargetsReached = true,
        timeout = Duration.INFINITE,
        stepsFromLastCovered = 3500L,
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
        stateCollectionStrategy = StateCollectionStrategy.REACHED_TARGET,
    )

    private val tsOptions = TsOptions(isTargetedModeEnabled = true)

    /**
     * Path to SDK in resources.
     */
    private val sdkPaths = listOf(
        "/sdk/ohos/5.0.1.111/ets/api",
        "/sdk/ohos/5.0.1.111/ets/arkts",
        "/sdk/ohos/5.0.1.111/ets/component",
        "/sdk/ohos/5.0.1.111/ets/kits",
    )

    private val useSDK = false

    private fun loadSdks(): List<EtsScene> {
        if (!useSDK) {
            return emptyList()
        }
        return sdkPaths.map {
            loadEtsProjectAutoConvert(getResourcePath(it))
        }
    }

    private fun loadProject(projectPath: Path): EtsScene {
        val project = loadEtsProjectAutoConvert(projectPath)
        val sdks = loadSdks()
        return EtsScene(
            projectFiles = project.projectFiles,
            sdkFiles = sdks.flatMap { it.projectFiles },
            projectName = project.projectName,
        )
    }

    private fun loadFile(path: String): EtsScene {
        val resourcePath = getResourcePath("/$path")
        val file = loadEtsFileAutoConvert(resourcePath)
        return EtsScene(listOf(file))
    }

    private fun loadTraces(path: String): List<TargetTrace> {
        val resourcePath = getResourcePath("/$path")
        return loadTraces(resourcePath)
    }

    private fun runOn(
        methodName: String,
        filePath: String,
        targetsPath: String,
    ) {
        val scene = loadFile(filePath)
        println("Loaded scene with ${scene.projectClasses.size} classes")
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == methodName }
        println("Method: $method")

        val traces = loadTraces(targetsPath)
        println("Loaded ${traces.size} traces")
        val targets = createTargetsFromTraces(listOf(method), traces)
        println("Created ${targets.size} targets with ${targets.sumOf { it.countLeaves() }} leaves")
        check(targets.isNotEmpty())

        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val results = machine.analyze(listOf(method), targets)
        println("Got ${results.size} results")
        results.let {}
    }

    @Test
    fun `run reachability on branch01`() {
        runOn(
            methodName = "branch01",
            filePath = "validation/branch01.ts",
            targetsPath = "validation/targets-branch01.json"
        )
    }

    @Test
    fun `run reachability on branch02`() {
        runOn(
            methodName = "branch02",
            filePath = "validation/branch02.ts",
            targetsPath = "validation/targets-branch02.json"
        )
    }

    @Test
    fun `run reachability on branch40`() {
        runOn(
            methodName = "branch40",
            filePath = "validation/branch40.ts",
            targetsPath = "validation/targets-branch40.json"
        )
    }

    @Test
    fun `run reachability on branch41`() {
        runOn(
            methodName = "branch41",
            filePath = "validation/branch41.ts",
            targetsPath = "validation/targets-branch41.json"
        )
    }

    @Test
    fun `run reachability on forOf03`() {
        options = options.copy(timeout = 10.seconds)
        runOn(
            methodName = "forOf03",
            filePath = "validation/forOf03.ts",
            targetsPath = "validation/targets-forOf03.json"
        )
    }

    @Test
    fun `run reachability on forOf05`() {
        options = options.copy(timeout = 10.seconds)
        runOn(
            methodName = "forOf05",
            filePath = "validation/forOf05.ts",
            targetsPath = "validation/targets-forOf05.json"
        )
    }

    @Test
    fun `run reachability on forOf07`() {
        options = options.copy(timeout = 10.seconds)
        runOn(
            methodName = "forOf07",
            filePath = "validation/forOf07.ts",
            targetsPath = "validation/targets-forOf07.json"
        )
    }
}
