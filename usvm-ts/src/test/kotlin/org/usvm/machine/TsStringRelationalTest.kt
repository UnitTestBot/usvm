package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath

class TsStringRelationalTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/StringRelational.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )
    override val tsOptions = TsOptions(maxArraySize = 2)

    @Test
    fun `literal strings use lexicographic UTF-16 ordering`() {
        for (name in listOf("literalLess", "unsignedCodeUnitOrder", "lengthOrder")) {
            val method = getMethod(methodName = name, className = "StringRelational")

            discoverProperties<TsTestValue.TsNumber>(
                method = method,
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `mixed string and number comparisons keep numeric coercion`() {
        val method = getMethod(methodName = "mixedNumberAndString", className = "StringRelational")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `symbolic string pairs compare their source values`() {
        val method = getMethod(methodName = "symbolicLess", className = "StringRelational")

        discoverProperties<TsTestValue.TsString, TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { left, right, result -> left.value < right.value && result.number == 1.0 },
            { left, right, result -> left.value >= right.value && result.number == 2.0 },
            invariants = arrayOf({ left, right, result ->
                val expected = if (left.value < right.value) 1.0 else 2.0

                result.number == expected
            }),
        )
    }

    @Test
    fun `conditional string alternative retains numeric sibling`() {
        val method = getMethod(methodName = "conditionalLeft", className = "StringRelational")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method,
            { useString, value, result -> useString.value && value.value < "b" && result.number == 1.0 },
            { useString, value, result -> useString.value && value.value >= "b" && result.number == 2.0 },
            { useString, _, result -> !useString.value && result.number == 2.0 },
            invariants = arrayOf({ useString, value, result ->
                val expected = if (useString.value && value.value < "b") 1.0 else 2.0

                result.number == expected
            }),
        )
    }
}
