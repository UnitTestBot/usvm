package org.usvm.samples

import org.junit.jupiter.api.Test

class BinaryOperatorsTest : PandaMethodTestRunner() {
    @Test
    fun testSumNumber() {
        discoverProperties<Any, Any>(
            MethodDescriptor(
                className = "BinaryOperators",
                methodName = "sumNumber",
                argumentsNumber = 1
            ),
            { a, result ->
                when (a) {
                    is Double -> result is Double && result == a + 1 + a
                    is Boolean -> result is Double && result == if (a) 3.0 else 1.0
                    is String -> {
                        val value = a.toDoubleOrNull() ?: a
                        result == if (value is Double) value + 1 + value else "${a}1${a}"
                    }
                    else -> TODO()
                }
            }
        )
    }
}