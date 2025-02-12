package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class Strings : TSMethodTestRunner() {
    override val scene: EtsScene = run {
        val name = "Strings.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testReturnConstantString() {
        val method = getMethod("Strings", "returnConstantString")
        discoverProperties<TSObject.TSString>(
            method = method,
            { r -> r.value == "Hello, World!" },
        )
    }

    @Test
    fun testStringConcatenation() {
        val method = getMethod("Strings", "concatenation")
        discoverProperties<TSObject.TSString, TSObject.TSString>(
            method = method,
            { a, r -> r.value == "Hello, ${a.value}!" },
        )
    }
}