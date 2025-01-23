package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class InstanceMethods : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "InstanceMethods.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testNoArgsInstanceMethod() {
        val method = getMethod("InstanceMethods", "noArguments")
        discoverProperties<TSObject.TSNumber>(
            method,
            { r -> r.number == 42.0 },
        )
    }

    @Test
    fun testSingleArgInstanceMethod() {
        val method = getMethod("InstanceMethods", "singleArgument")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, r -> a.number == 1.0 && r.number == 100.0 },
            { a, r -> a.number != 1.0 && r.number == 0.0 },
        )
    }

    @Test
    fun testManyArgsInstanceMethod() {
        val method = getMethod("InstanceMethods", "manyArguments")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, _, _, _, r -> a.number == 1.0 && r == a },
            { _, b, _, _, r -> b.number == 2.0 && r == b },
            { _, _, c, _, r -> c.number == 3.0 && r == c },
            { _, _, _, d, r -> d.number == 4.0 && r == d },
            { a, b, c, d, r ->
                a.number != 1.0 && b.number != 2.0 && c.number != 3.0 && d.number != 4.0 && r.number == 100.0
            },
        )
    }
}
