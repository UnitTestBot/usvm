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

    @Test
    fun testDivideNumber() {
        discoverProperties<Any, Any>(
            MethodDescriptor(
                className = "BinaryOperators",
                methodName = "divideNumber",
                argumentsNumber = 1
            ),
            { a, result ->
                when (a) {
                    is Double -> result is Double && if (a == 0.0) result.isNaN() else result == a / 3 / a
                    is Boolean -> {
                        if (a) {
                            result is Double && result == 1.0 / 3
                        } else {
                            result is Double && result.isNaN()
                        }
                    }
                    else -> result is Double && result.isNaN()
                }
            }
        )
    }
}