package org.usvm.samples.checkers

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.checkers.UnreachableCodeDetector
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath

class UnreachableCodeDetectorTest {
    val scene = run {
        val name = "UnreachableCode.ts"
        val path = getResourcePath("/samples/checkers/$name")
        val file = loadEtsFileAutoConvert(
            path,
            useArkAnalyzerTypeInference = 1
        )
        EtsScene(listOf(file))
    }
    val options = UMachineOptions()
    val tsOptions = TsOptions(interproceduralAnalysis = false)

    @Test
    fun testUnreachableCode() {
        val observer = UnreachableCodeDetector()
        val machine = TsMachine(scene, options, tsOptions, observer, observer)
        val methods = scene.projectClasses
            .flatMap { it.methods }
            .filterNot { it.cfg.stmts.isEmpty() }
            .filter { it.name == "simpleUnreachableBranch" }
        machine.analyze(methods)

        val uncoveredResults = observer.result.values.singleOrNull() ?: error("No results found")
        val uncoveredStatements = uncoveredResults.singleOrNull()

        check(uncoveredStatements != null) { "Uncovered statements are incorrect, results are $uncoveredStatements" }
    }

    @Test
    fun testUnreachableCodeWithMockedCallsInside() {
        val observer = UnreachableCodeDetector()
        val tsOptions = TsOptions(interproceduralAnalysis = false)
        val machine = TsMachine(scene, options, tsOptions, observer, observer)
        val methods = scene.projectClasses
            .flatMap { it.methods }
            .filter { it.name == "unreachableCodeWithCallsInside" }
        machine.analyze(methods)

        val results = observer.result.values.singleOrNull() ?: error("No results found")
        check(results.single().second.single() is EtsAssignStmt)
    }

    @Test
    fun testUnreachableCodeCallsInside() {
        val observer = UnreachableCodeDetector()
        val tsOptions = TsOptions(interproceduralAnalysis = true)
        val machine = TsMachine(scene, options, tsOptions, observer, observer)
        val methodName = "unreachableCodeWithCallsInside"
        val methods = scene.projectClasses
            .flatMap { it.methods }
            .filter { it.name == methodName }

        machine.analyze(methods)

        val results = observer.result.entries

        check(results.size == 2)

        val relatedBranch = results.single { it.key.name == methodName }
        val stmts = relatedBranch.value.single()
        check(stmts.second.single() is EtsIfStmt)
    }
}
