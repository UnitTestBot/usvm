package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class Arguments : TSMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "Arguments.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testNoArgs() {
        val method = getMethod("SimpleClass", "noArguments")
        discoverProperties<TSObject.TSNumber>(
            method,
            { r -> r.number == 42.0 },
        )
    }

    @Test
    fun testSingleArg() {
        val method = getMethod("SimpleClass", "singleArgument")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, r -> a == r },
        )
    }

    @Test
    fun testManyArgs() {
        val method = getMethod("SimpleClass", "manyArguments")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            method,
            { a, _, _, r -> a.number == 1.0 && r == a },
            { _, b, _, r -> b.number == 2.0 && r == b },
            { _, _, c, r -> c.number == 3.0 && r == c },
            { a, b, c, r ->
                a.number != 1.0 && b.number != 2.0 && c.number != 3.0 && r.number == 100.0
            },
        )
    }

    @Test
    @Disabled
    fun testThisArg() {
        val method = getMethod("SimpleClass", "thisArgument")
        discoverProperties<TSObject.TSNumber>(
            method,
        )
    }
}
