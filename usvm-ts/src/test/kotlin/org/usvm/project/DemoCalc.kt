package org.usvm.project

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.getDeclaredLocals
import org.jacodb.ets.utils.getLocals
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.fixHome
import kotlin.io.path.Path
import kotlin.test.Test

class RunOnDemoCalcProject : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        loadEtsProjectFromIR(
            Path("~/dev/jacodb/jacodb-ets/src/test/resources/projects/Demo_Calc/etsir/entry").fixHome(),
            Path("~/dev/ark/sdk/etsir/ohos/5.0.1.111/ets").fixHome(),
        )
    }

    @Test
    fun `test run on each method`() {
        println("Total classes: ${scene.projectAndSdkClasses.size}")
        for (clazz in scene.projectAndSdkClasses) {
            println()
            println("CLASS: ${clazz.name} in ${clazz.signature.file}")
            for (method in clazz.methods) {
                println()
                println("METHOD: ${clazz.name}::${method.name}(${method.parameters.joinToString()})")
                if (method.cfg.stmts.isEmpty()) {
                    println("CFG is empty")
                    continue
                }
                if (method.getLocals() != method.getDeclaredLocals()) {
                    println(
                        "Locals mismatch:\n  getLocals() = ${
                            method.getLocals().sortedBy { it.name }
                        }\n  getDeclaredLocals() = ${
                            method.getDeclaredLocals().sortedBy { it.name }
                        }"
                    )
                    // continue
                }
                discoverProperties<TsTestValue>(method = method)
            }
        }
    }

    @Test
    fun `test run on all methods`() {
        val methods = scene.projectClasses
            .filterNot { it.name.startsWith("%AC") }
            .flatMap {
                it.methods
                    .filterNot { it.cfg.stmts.isEmpty() }
                    .filterNot { it.isStatic }
                    .filterNot { it.name.startsWith("%AM") }
                    .filterNot { it.name == "build" }
            }
        TsMachine(scene, options).use { machine ->
            val states = machine.analyze(methods)
            states.let {}
        }
    }
}
