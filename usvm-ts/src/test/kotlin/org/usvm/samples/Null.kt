package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.RepeatedTest
import org.usvm.api.TsObject
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class Null : TsMethodTestRunner() {

    override val scene = run {
        val name = "Null.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @RepeatedTest(20)
    fun testIsNull() {
        val method = getMethod("Null", "isNull")
        discoverProperties<TsObject, TsObject.TsNumber>(
            method,
            { a, r -> a is TsObject.TsNull && r.number == 1.0 },
            { a, r -> a !is TsObject.TsNull && r.number == 2.0 },
        )
    }
}
