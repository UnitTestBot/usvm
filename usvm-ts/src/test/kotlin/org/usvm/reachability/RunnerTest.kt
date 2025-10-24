package org.usvm.reachability

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Tag
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
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
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.time.Duration

@Tag("manual")
class RunnerTest {

    //! -----
    //! Note: "current directory" is "usvm-ts" module
    //! -----

    private val options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        exceptionsPropagation = true,
        stopOnTargetsReached = true,
        timeout = Duration.INFINITE,
        stepsFromLastCovered = 3500L,
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
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
        return EtsScene(listOf(file), projectName = resourcePath.parent.name)
    }

    private fun loadTraces(path: String): List<TargetTrace> {
        val resourcePath = getResourcePath("/$path")
        return loadTraces(resourcePath)
    }

    @Test
    fun `run reachability on branch01`() {
        val scene = loadFile("validation/branch01.ts")

        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "branch01" }

        val traces = loadTraces("validation/targets-branch01.json")
        println("Loaded ${traces.size} traces")
        val targets = createTargetsFromTraces(listOf(method), traces)
        println("Created ${targets.size} targets with ${targets.sumOf { it.countLeaves() }} leaves")
        check(targets.isNotEmpty())

        val results = machine.analyze(listOf(method), targets)
        println("Got ${results.size} results")
        results.let {}
    }

    @Test
    fun `run reachability on branch02`() {
        val scene = loadFile("validation/branch02.ts")

        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "branch02" }

        val traces = loadTraces("validation/targets-branch02.json")
        println("Loaded ${traces.size} traces")
        val targets = createTargetsFromTraces(listOf(method), traces)
        println("Created ${targets.size} targets with ${targets.sumOf { it.countLeaves() }} leaves")
        check(targets.isNotEmpty())

        val results = machine.analyze(listOf(method), targets)
        println("Got ${results.size} results")
        results.let {}
    }

    @Test
    fun `run reachability on branch41`() {
        val scene = loadFile("validation/branch41.ts")

        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "branch41" }

        val traces = loadTraces("validation/targets-branch41.json")
        println("Loaded ${traces.size} traces")
        val targets = createTargetsFromTraces(listOf(method), traces)
        println("Created ${targets.size} targets with ${targets.sumOf { it.countLeaves() }} leaves")
        check(targets.isNotEmpty())

        val results = machine.analyze(listOf(method), targets)
        println("Got ${results.size} results")
        results.let {}
    }
}
