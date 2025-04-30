package org.usvm.project

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.getDeclaredLocals
import org.jacodb.ets.utils.getLocals
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import org.usvm.util.getResourcePathOrNull
import kotlin.test.Test

@EnabledIf("projectAvailable")
class RunOnDemoCalcProject : TsMethodTestRunner() {

    companion object {
        private const val PROJECT_PATH = "/projects/Demo_Calc/etsir/entry"
        private const val SDK_PATH = "/sdk/ohos/etsir"

        @JvmStatic
        private fun projectAvailable(): Boolean {
            return getResourcePathOrNull(PROJECT_PATH) != null
        }
    }

    override val scene: EtsScene = run {
        val projectPath = getResourcePath(PROJECT_PATH)
        val sdkPath = getResourcePathOrNull(SDK_PATH)
            ?: error("Could not load SDK from resources '$SDK_PATH'. Try running './gradlew generateSdkIR' to generate it.")
        loadEtsProjectFromIR(projectPath, sdkPath)
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
        val tsOptions = TsOptions()
        TsMachine(scene, options, tsOptions).use { machine ->
            val states = machine.analyze(methods)
            states.let {}
        }
    }
}
