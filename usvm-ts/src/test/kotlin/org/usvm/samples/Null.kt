package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.RepeatedTest
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class Null : TSMethodTestRunner() {

    override val scene = run {
        val name = "Null.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @RepeatedTest(20)
    fun testIsNull() {
        val method = getMethod("Null", "isNull")
        discoverProperties<TSObject, TSObject.TSNumber>(
            method,
            { a, r -> a is TSObject.TSNull && r.number == 1.0 },
            { a, r -> a !is TSObject.TSNull && r.number == 2.0 },
        )
    }
}