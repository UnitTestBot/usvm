package org.usvm.samples.checkers

import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.checkers.UnreachableCodeDetector
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.getResourcePath

class UnreachableCodeDetectorTest {
    @Test
    fun testUnreachableCode() {
        // val scene = run {
        //     val name = "UnreachableCode.ts"
        //     val path = getResourcePath("/samples/checkers/$name")
        //     val file = loadEtsFileAutoConvert(
        //         path,
        //         useArkAnalyzerTypeInference = 1
        //     )
        //     EtsScene(listOf(file))
        // }

        val path = getResourcePath("/projects/Demo_Calc/etsir")
        val scene = loadEtsProjectFromIR(path, null)

        val options = UMachineOptions()
        val tsOptions = TsOptions(interproceduralAnalysis = false)
        val observer = UnreachableCodeDetector()
        val machine = TsMachine(scene, options, tsOptions, observer, observer)
        val methods = scene.projectClasses
            .flatMap { it.methods }
            .filterNot { it.cfg.stmts.isEmpty() }
            //.filter { it.name == "simpleUnreachableBranch" }
        val results = machine.analyze(methods)

        check(results.isNotEmpty())
    }
}
