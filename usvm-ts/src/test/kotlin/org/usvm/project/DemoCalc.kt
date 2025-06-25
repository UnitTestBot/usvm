package org.usvm.project

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.junit.jupiter.api.condition.EnabledIf
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
            return getResourcePathOrNull(PROJECT_PATH) != null && getResourcePathOrNull(SDK_PATH) != null
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
        val exceptions = mutableListOf<Throwable>()
        val classes = scene.projectClasses.filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }

        println("Total classes: ${classes.size}")

        classes
            .forEach { cls ->
                val methods = cls.methods
                    .filterNot { it.cfg.stmts.isEmpty() }
                    .filterNot { it.isStatic }
                    .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                    .filterNot { it.name == "build" }
                    .filterNot { it.name == INSTANCE_INIT_METHOD_NAME }
                    .filterNot { it.name == STATIC_INIT_METHOD_NAME }
                    .filterNot { it.name == CONSTRUCTOR_NAME }

                if (methods.isEmpty()) return@forEach

                runCatching {
                    val tsOptions = TsOptions()
                    TsMachine(scene, options, tsOptions).use { machine ->
                        val states = machine.analyze(methods)
                        states.let {}
                    }
                }.onFailure {
                    exceptions += it
                }
            }

        val exc = exceptions.groupBy { it }
        println("Total exceptions: ${exc.size}")
        for (es in exc.values.sortedBy { it.size }.asReversed()) {
            println("${es.first()}")
        }
    }

    @Test
    fun `test run on all methods`() {
        val methods = scene.projectClasses
            .filterNot { cls -> cls.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
            .flatMap { cls ->
                cls.methods
                    .filterNot { it.cfg.stmts.isEmpty() }
                    .filterNot { it.isStatic }
                    .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                    .filterNot { it.name == "build" }
            }
        val tsOptions = TsOptions()
        TsMachine(scene, options, tsOptions).use { machine ->
            val states = machine.analyze(methods)
            states.let {}
        }
    }

    @Test
    fun `test on particular method`() {
        val method = scene.projectClasses
            .flatMap { it.methods }
            .single { it.name == "createKvStore" && it.enclosingClass?.name == "KvStoreModel" }
        val tsOptions = TsOptions()
        TsMachine(scene, options, tsOptions).use { machine ->
            val states = machine.analyze(listOf(method))
            states.let {}
        }
    }
}
