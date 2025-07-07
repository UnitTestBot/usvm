package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import kotlin.test.Test

class Async : TsMethodTestRunner() {

    companion object {
        private const val SDK_TYPESCRIPT_PATH = "/sdk/typescript"
        private const val SDK_OHOS_PATH = "/sdk/ohos"
    }

    private val className = this::class.simpleName!!

    override val scene: EtsScene = loadSampleScene(
        className,
        sdks = listOf(SDK_TYPESCRIPT_PATH, SDK_OHOS_PATH)
    )

    @Test
    fun `create and await promise`() {
        val method = getMethod(className, "createAndAwaitPromise")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r.number == 1.0 },
            invariants = arrayOf(
                { r -> r.number != -1.0 },
            )
        )
    }
}
