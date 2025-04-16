package org.usvm.samples.checkers

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

    @Test
    fun testUnreachableCode() {
        val options = UMachineOptions()
        val tsOptions = TsOptions(interproceduralAnalysis = false)
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
}
