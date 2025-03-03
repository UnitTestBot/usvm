package org.usvm.project

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.getDeclaredLocals
import org.jacodb.ets.utils.getLocals
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.fixHome
import kotlin.io.path.Path
import kotlin.test.Test

class RunOnDemoCalcProject : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        loadEtsProjectFromIR(
            Path("~/dev/jacodb/jacodb-ets/src/test/resources/projects/Demo_Calc/etsir/entry").fixHome(),
            null
        )
    }

    @Test
    fun `test run on all methods`() {
        println("Total classes: ${scene.projectAndSdkClasses.size}")
        for (clazz in scene.projectAndSdkClasses) {
            println()
            println("CLASS: ${clazz.name} in ${clazz.signature.file}")
            for (method in clazz.methods) {
                println()
                println("METHOD: ${clazz.name}::${method.name} (${method.parameters.joinToString()})")
                if (method.cfg.stmts.isEmpty()) {
                    println("CFG is empty")
                    continue
                }
                if (method.getLocals() != method.getDeclaredLocals()) {
                    println("Locals: ${method.getLocals()} != ${method.getDeclaredLocals()}")
                    continue
                }
                discoverProperties<TsValue>(method = method)
            }
        }
    }
}
