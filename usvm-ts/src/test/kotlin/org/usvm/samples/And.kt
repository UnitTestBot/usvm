package org.usvm.samples

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TSObject
import org.usvm.util.TSMethodTestRunner
import org.usvm.util.getResourcePath

class And : TSMethodTestRunner() {
    override val scene: EtsScene
        get() = run {
            val name = "And.ts"
            val path = getResourcePath("/samples/$name")
            val file = loadEtsFileAutoConvert(path)
            EtsScene(listOf(file))
        }

    @Test
    fun testAndForTwoBoolValues() {
        val method = getMethod("And", "andForTwoBoolValues")
        discoverProperties<TSObject.TSBoolean, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && b.value && r.number == 1.0 },
            { a, b, r -> a.value && !b.value && r.number == 2.0 },
            { a, b, r -> !a.value && b.value && r.number == 3.0 },
            { a, b, r -> !a.value && !b.value && r.number == 4.0 },
        )
    }

    @Test
    fun testAndForUnknownTypes() {
        val method = getMethod("And", "andForUnknownTypes")
        discoverProperties<TSObject, TSObject, TSObject.TSNumber>(
            method = method,
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    a.value && b.value && r.number == 1.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    a.value && !b.value && r.number == 2.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && b.value && r.number == 3.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && !b.value && r.number == 4.0
                } else true
            },
        )
    }

    @Test
    fun testTruthyUnknown() {
        val method = getMethod("And", "truthyUnknown")
        discoverProperties<TSObject, TSObject, TSObject.TSNumber>(
            method = method,
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    a.value && !b.value && r.number == 1.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && b.value && r.number == 2.0
                } else true
            },
            { a, b, r ->
                if (a is TSObject.TSBoolean && b is TSObject.TSBoolean) {
                    !a.value && !b.value && r.number == 99.0
                } else true
            },
        )
    }
}
