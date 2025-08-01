package org.usvm.checkers

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ReachabilityChecker {
    val scene = run {
        val name = "ReachabilityChecker.ts"
        val path = getResourcePath("/checkers/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }
    val options = UMachineOptions(
        stopOnTargetsReached = true,
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        timeout = 15000000.seconds
    )
    val tsOptions = TsOptions(enableVisualization = true)

    @Test
    fun runReachabilityCheck() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "simpleFunction" }

        val initialPoint = method.cfg.stmts.first { "if (" in it.toString() }
        val initialTarget = TsReachabilityTarget.InitialPoint(initialPoint)

        val intermediatePoint = method.cfg.stmts.filter { "if (" in it.toString() }[1]
        val intermediateTarget = TsReachabilityTarget.IntermediatePoint(intermediatePoint).also {
            initialTarget.addChild((it))
        }
        val finalPoint = method.cfg.stmts.first { "return" in it.toString() }
        TsReachabilityTarget.FinalPoint(finalPoint).also {
            intermediateTarget.addChild(it)
        }

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        require(results.size == 1) { "Expected exactly one result, but got ${results.size}" }
    }

    @Test
    fun runReachabilityCheckForFirstInstruction() {
        val machine = TsMachine(scene, options, tsOptions, machineObserver = ReachabilityObserver())
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "simpleFunction" }

        val initialPoint = method.cfg.stmts.first()
        val initialTarget = TsReachabilityTarget.InitialPoint(initialPoint)

        val results = machine.analyze(listOf(method), listOf(initialTarget))
        require(results.isEmpty()) { "Expected no analysis results, but got ${results.size}" }
        require(initialTarget.isRemoved) { "Expected initial target to be removed, but it was not" }
    }
}
