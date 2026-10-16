package org.usvm.samples.lang

import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class JavaScriptModuleReceiverTest : TsMethodTestRunner() {
    override val scene = loadEtsProjectAutoConvert(
        projectPath = getResourcePath(res = "/samples/lang/javascript-module-receiver"),
        provider = EtsIrProvider.TS_FRONTEND,
    )

    @Test
    fun `imported JavaScript module receiver remains truthy`() {
        val method = getMethod(methodName = "read", className = "JavaScriptModuleReceiver")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result.number == 17.0 },
            invariants = arrayOf({ result -> result.number == 17.0 }),
        )
    }
}
