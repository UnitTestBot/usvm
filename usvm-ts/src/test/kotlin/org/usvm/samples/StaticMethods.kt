package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TsValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test

class StaticMethods : TsMethodTestRunner() {

    override val scene: EtsScene = run {
        val name = "StaticMethods.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(path)
        EtsScene(listOf(file))
    }

    @Test
    fun testNoArgsStaticMethod() {
        val method = getMethod("StaticMethods", "noArguments")
        discoverProperties<TsValue.TsNumber>(
            method,
            { r -> r.number == 42.0 },
        )
    }

    @Test
    fun testSingleArgStaticMethod() {
        val method = getMethod("StaticMethods", "singleArgument")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber>(
            method,
            { a, r -> a.number == 1.0 && r.number == 100.0 },
            { a, r -> a.number != 1.0 && r.number == 0.0 },
        )
    }

    @Test
    fun testManyArgsStaticMethod() {
        val method = getMethod("StaticMethods", "manyArguments")
        discoverProperties<TsValue.TsNumber, TsValue.TsNumber, TsValue.TsNumber, TsValue.TsNumber, TsValue.TsNumber>(
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
