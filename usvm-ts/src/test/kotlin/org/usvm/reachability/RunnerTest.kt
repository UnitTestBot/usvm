package org.usvm.reachability

import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Tag
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTarget
import org.usvm.api.reachability.TsReachabilityObserver
import org.usvm.api.reachability.TsReachabilityTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath
import kotlin.io.path.Path
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

    @Test
    fun `run reachability on branch01`() {
        val projectPath = Path("../examples/reachability/projects/branch")
        val scene = run {
            val project = loadEtsProjectAutoConvert(projectPath)
            val sdks = loadSdks()
            EtsScene(
                projectFiles = project.projectFiles,
                sdkFiles = sdks.flatMap { it.projectFiles },
                projectName = project.projectName,
            )
        }

        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "branch01" }

        val initialTarget: TsTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target = target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        println("Got ${results.size} results")
        results.let {}
    }

    @Test
    fun `run reachability on branch02`() {
        val projectPath = Path("../examples/reachability/projects/branch")
        val scene = run {
            val project = loadEtsProjectAutoConvert(projectPath)
            val sdks = loadSdks()
            EtsScene(
                projectFiles = project.projectFiles,
                sdkFiles = sdks.flatMap { it.projectFiles },
                projectName = project.projectName,
            )
        }

        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "branch02" }

        val initialTarget: TsTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        val returnStmt = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()[0]
        target = target.addChild(TsReachabilityTarget.FinalPoint(returnStmt))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        println("Got ${results.size} results")
        results.let {}
    }

    @Test
    fun `run reachability on branch41`() {
        val projectPath = Path("../examples/reachability/projects/branch")
        val scene = run {
            val project = loadEtsProjectAutoConvert(projectPath)
            val sdks = loadSdks()
            EtsScene(
                projectFiles = project.projectFiles,
                sdkFiles = sdks.flatMap { it.projectFiles },
                projectName = project.projectName,
            )
        }

        val machine = TsMachine(scene, options, tsOptions, machineObserver = TsReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "branch41" }

        val initialTarget: TsTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        var target: TsTarget = initialTarget

        target = target.addChild(TsReachabilityTarget.IntermediatePoint(method.cfg.blocks[4].statements.first()))
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(method.cfg.blocks[1].statements.first()))
        target = target.addChild(TsReachabilityTarget.IntermediatePoint(method.cfg.blocks[2].statements.first()))

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        println("Got ${results.size} results")
        results.let {}
    }
}
