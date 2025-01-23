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

    private fun isTruthy(x: Double): Boolean {
        return x != 0.0 && !x.isNaN()
    }

    private fun isTruthy(x: TSObject.TSNumber): Boolean {
        return isTruthy(x.number)
    }

    private fun isTruthy(x: TSObject.TSObject): Boolean {
        return x.addr != 0
    }

    @Test
    fun `test andOfBooleanAndBoolean`() {
        val method = getMethod("And", "andOfBooleanAndBoolean")
        discoverProperties<TSObject.TSBoolean, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && b.value && r.number == 1.0 },
            { a, b, r -> a.value && !b.value && r.number == 2.0 },
            { a, b, r -> !a.value && b.value && r.number == 3.0 },
            { a, b, r -> !a.value && !b.value && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfNumberAndNumber`() {
        val method = getMethod("And", "andOfNumberAndNumber")
        discoverProperties<TSObject.TSNumber, TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && !isTruthy(b) && r.number == 2.0 },
            { a, b, r -> !isTruthy(a) && isTruthy(b) && r.number == 3.0 },
            { a, b, r -> !isTruthy(a) && !isTruthy(b) && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfBooleanAndNumber`() {
        val method = getMethod("And", "andOfBooleanAndNumber")
        discoverProperties<TSObject.TSBoolean, TSObject.TSNumber, TSObject.TSNumber>(
            method = method,
            { a, b, r -> a.value && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> a.value && !isTruthy(b) && r.number == 2.0 },
            { a, b, r -> !a.value && isTruthy(b) && r.number == 3.0 },
            { a, b, r -> !a.value && !isTruthy(b) && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfNumberAndBoolean`() {
        val method = getMethod("And", "andOfNumberAndBoolean")
        discoverProperties<TSObject.TSNumber, TSObject.TSBoolean, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && b.value && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && !b.value && r.number == 2.0 },
            { a, b, r -> !isTruthy(a) && b.value && r.number == 3.0 },
            { a, b, r -> !isTruthy(a) && !b.value && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfObjectAndObject`() {
        val method = getMethod("And", "andOfObjectAndObject")
        discoverProperties<TSObject.TSObject, TSObject.TSObject, TSObject.TSNumber>(
            method = method,
            { a, b, r -> isTruthy(a) && isTruthy(b) && r.number == 1.0 },
            { a, b, r -> isTruthy(a) && !isTruthy(b) && r.number == 2.0 },
            { a, b, r -> !isTruthy(a) && isTruthy(b) && r.number == 3.0 },
            { a, b, r -> !isTruthy(a) && !isTruthy(b) && r.number == 4.0 },
        )
    }

    @Test
    fun `test andOfUnknown`() {
        val method = getMethod("And", "andOfUnknown")
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
    fun `test truthyUnknown`() {
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
