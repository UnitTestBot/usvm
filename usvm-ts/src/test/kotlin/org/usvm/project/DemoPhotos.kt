package org.usvm.project

import mu.KotlinLogging
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import org.usvm.util.getResourcePathOrNull
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

@EnabledIf("projectAvailable")
class RunOnDemoPhotosProject : TsMethodTestRunner() {

    companion object {
        private const val PROJECT_PATH = "/projects/Demo_Photos/source/entry"
        private const val SDK_PATH = "/sdk/ohos/etsir"

        @JvmStatic
        private fun projectAvailable(): Boolean {
            val isProjectPresent = getResourcePathOrNull(PROJECT_PATH) != null
            val isSdkPreset = getResourcePathOrNull(SDK_PATH) != null
            return isProjectPresent && isSdkPreset
        }
    }

    override val scene: EtsScene = run {
        val projectPath = getResourcePath(PROJECT_PATH)
        val sdkPath = getResourcePathOrNull(SDK_PATH)
            ?: error(
                "Could not load SDK from resources '$SDK_PATH'. " +
                    "Try running './gradlew generateSdkIR' to generate it."
            )
        loadEtsProjectAutoConvert(projectPath, sdkPath)
    }

    @Test
    fun `test run on each class`() {
        val exceptions = mutableListOf<Throwable>()
        val classes = scene.projectClasses
            .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }

        logger.info { "Total classes: ${classes.size}" }

        for (cls in classes) {
            logger.info {
                "Analyzing class ${cls.name} with ${cls.methods.size} methods"
            }

            val methods = cls.methods
                .filterNot { it.cfg.stmts.isEmpty() }
                .filterNot { it.isStatic }
                .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                .filterNot { it.name == "build" }
                .filterNot { it.name == INSTANCE_INIT_METHOD_NAME }
                .filterNot { it.name == STATIC_INIT_METHOD_NAME }
                .filterNot { it.name == CONSTRUCTOR_NAME }
                .filterNot { it.name == "loadFileAsset" }
                .filterNot { it.name == "onRecover" }

            if (methods.isEmpty()) continue

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
        logger.error { "Total exceptions: ${exc.size}" }
        for (es in exc.values.sortedBy { it.size }.asReversed()) {
            logger.error { "${es.first()}" }
        }
    }

    @Test
    fun `test run on all methods`() {
        val methods = scene.projectClasses
            .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
            .flatMap { cls ->
                cls.methods
                    .filterNot { it.cfg.stmts.isEmpty() }
                    .filterNot { it.isStatic }
                    .filterNot { it.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                    .filterNot { it.name == "build" }
                    .filterNot { it.name == INSTANCE_INIT_METHOD_NAME }
                    .filterNot { it.name == STATIC_INIT_METHOD_NAME }
                    .filterNot { it.name == CONSTRUCTOR_NAME }
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
            .single { it.name == "onSelect" && it.enclosingClass?.name == "AlbumSetPage" }

        val tsOptions = TsOptions()
        TsMachine(scene, options, tsOptions).use { machine ->
            val states = machine.analyze(listOf(method))
            states.let {}
        }
    }
}
