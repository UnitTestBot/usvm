package org.usvm.samples

import kotlin.test.Test

class BinaryOperatorsForLiteralsTest : PandaMethodTestRunner() {
    @Test
    fun testSumNumber() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumNumber",
                argumentsNumber = 0
            ),
            { r -> r == 2.0 }
        )
    }

    @Test
    fun testDivideNumber() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideNumber",
                argumentsNumber = 0
            ),
            { r -> r == 2.0 / 3 }
        )
    }

    @Test
    fun testSumNumBool() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumNumBool",
                argumentsNumber = 0
            ),
            { r -> r == 2.0 }
        )
    }

    @Test
    fun testDivideNumBool() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideNumBool",
                argumentsNumber = 0
            ),
            { r -> r == 1.0 }
        )
    }

    @Test
    fun testSumNumString() {
        discoverProperties<String>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumNumString",
                argumentsNumber = 0
            ),
            { r -> r == "1true"}
        )
    }

    @Test
    fun testDivideNumString() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideNumString",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumNumObj() {
        discoverProperties<String>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumNumObj",
                argumentsNumber = 0
            ),
            { r -> TODO() }
        )
    }

    @Test
    fun testDivideNumObj() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideNumObj",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumBoolNum() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumBoolNum",
                argumentsNumber = 0
            ),
            { r -> r == 2.0 }
        )
    }

    @Test
    fun testDivideBoolNum() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideBoolNum",
                argumentsNumber = 0
            ),
            { r -> r == 1.0 }
        )
    }

    @Test
    fun testSumBoolBool() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumBoolBool",
                argumentsNumber = 0
            ),
            { r -> r == 2.0 }
        )
    }

    @Test
    fun testDivideBoolBool() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideBoolBool",
                argumentsNumber = 0
            ),
            { r -> r == 1.0 }
        )
    }

    @Test
    fun testSumBoolString() {
        discoverProperties<String>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumBoolString",
                argumentsNumber = 0
            ),
            { r -> r == "truetrue" }
        )
    }

    @Test
    fun testDivideBoolString() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideBoolString",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumBoolObj() {
        discoverProperties<String>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumBoolObj",
                argumentsNumber = 0
            ),
            { r -> TODO() }
        )
    }

    @Test
    fun testDivideBoolObj() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideBoolObj",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }
    
    @Test
    fun testSumStringNum() {
        discoverProperties<String>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumStringNum",
                argumentsNumber = 0
            ),
            { r -> r == "true1" }
        )
    }

    @Test
    fun testDivideStringNum() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideStringNum",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true}
        )
    }

    @Test
    fun testSumStringBool() {
        discoverProperties<String>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumStringBool",
                argumentsNumber = 0
            ),
            { r -> r == "truetrue" }
        )
    }

    @Test
    fun testDivideStringBool() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideStringBool",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumStringString() {
        discoverProperties<String>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumStringString",
                argumentsNumber = 0
            ),
            { r -> r == "truetrue" }
        )
    }

    @Test
    fun testDivideStringString() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideStringString",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumStringObj() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumStringObj",
                argumentsNumber = 0
            ),
            { r -> TODO() }
        )
    }

    @Test
    fun testDivideStringObj() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideStringObj",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumObjNum() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumObjNum",
                argumentsNumber = 0
            ),
            { r -> TODO() }
        )
    }

    @Test
    fun testDivideObjNum() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideObjNum",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumObjBool() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumObjBool",
                argumentsNumber = 0
            ),
            { r -> TODO() }
        )
    }

    @Test
    fun testDivideObjBool() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideObjBool",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumObjString() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumObjString",
                argumentsNumber = 0
            ),
            { r -> TODO() }
        )
    }

    @Test
    fun testDivideObjString() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideObjString",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }

    @Test
    fun testSumObjObj() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "sumObjObj",
                argumentsNumber = 0
            ),
            { r -> TODO() }
        )
    }

    @Test
    fun testDivideObjObj() {
        discoverProperties<Double>(
            MethodDescriptor(
                className = "BinaryOperatorsForLiterals",
                methodName = "divideObjObj",
                argumentsNumber = 0
            ),
            { r -> r?.isNaN() == true }
        )
    }
}