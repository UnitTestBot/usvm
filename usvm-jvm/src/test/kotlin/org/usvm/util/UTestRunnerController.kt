package org.usvm.util

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL
import org.usvm.api.util.UTestRunner


class UTestRunnerController : BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    companion object {
        var started = false
    }


    override fun beforeAll(context: ExtensionContext) {
        if (!started) {
            started = true
            context.root.getStore(GLOBAL).put("runner callback", this)
        }
    }

    override fun close() {
        UTestRunner.runner.close()
    }
}