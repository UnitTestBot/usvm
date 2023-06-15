package org.usvm.operators

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.operator.JcUnaryOperator
import kotlin.test.assertEquals

class JcUnaryOperatorTest {
    lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
    }

    @Test
    fun `Test cast int to byte`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToByte,
            "(byte)",
            { operand -> operand.toByte().toInt() },
            ::extractInt
        )

    @Test
    fun `Test cast int to char`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToChar,
            "(char)",
            { operand -> operand.toChar().code },
            ::extractInt
        )

    @Test
    fun `Test cast int to short`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToShort,
            "(short)",
            { operand -> operand.toShort().toInt() },
            ::extractInt
        )

    @Test
    fun `Test cast int to long`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToLong,
            "(long)",
            { operand -> operand.toLong() },
            ::extractLong
        )

    @Test
    fun `Test cast int to float`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToFloat,
            "(float)",
            { operand -> operand.toFloat() },
            ::extractFloat
        )

    @Test
    fun `Test cast int to double`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToDouble,
            "(double)",
            { operand -> operand.toDouble() },
            ::extractDouble
        )

    @Test
    fun `Test cast long to int`() =
        testOperatorOnLong(
            JcUnaryOperator.CastToInt,
            "(int)",
            { operand -> operand.toInt() },
            ::extractInt
        )

    @Test
    fun `Test cast long to float`() =
        testOperatorOnLong(
            JcUnaryOperator.CastToFloat,
            "(float)",
            { operand -> operand.toFloat() },
            ::extractFloat
        )


    @Test
    fun `Test cast long to double`() =
        testOperatorOnLong(
            JcUnaryOperator.CastToDouble,
            "(double)",
            { operand -> operand.toDouble() },
            ::extractDouble
        )

    @Test
    fun `Test cast float to int`() =
        testOperatorOnFloat(
            JcUnaryOperator.CastToInt,
            "(int)",
            { operand -> operand.toInt() },
            ::extractInt
        )

    @Test
    fun `Test cast float to long`() =
        testOperatorOnFloat(
            JcUnaryOperator.CastToLong,
            "(long)",
            { operand -> operand.toLong() },
            ::extractLong
        )


    @Test
    fun `Test cast float to double`() =
        testOperatorOnFloat(
            JcUnaryOperator.CastToDouble,
            "(double)",
            { operand -> operand.toDouble() },
            ::extractDouble
        )

    @Test
    fun `Test cast double to int`() =
        testOperatorOnDouble(
            JcUnaryOperator.CastToInt,
            "(int)",
            { operand -> operand.toInt() },
            ::extractInt
        )

    @Test
    fun `Test cast double to long`() =
        testOperatorOnDouble(
            JcUnaryOperator.CastToLong,
            "(long)",
            { operand -> operand.toLong() },
            ::extractLong
        )


    @Test
    fun `Test cast double to float`() =
        testOperatorOnDouble(
            JcUnaryOperator.CastToFloat,
            "(float)",
            { operand -> operand.toFloat() },
            ::extractFloat
        )

    private fun <T> testOperatorOnInt(
        operator: JcUnaryOperator,
        operatorText: String,
        onInt: (Int) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) {
        intData.map { operand ->
            val expr = ctx.mkBv(operand)
            val result = operator(expr)

            val expected = try {
                onInt(operand)
            } catch (_: ArithmeticException) {
                null
            }
            val actual = extractFromUExpr(result)

            assertEquals(expected, actual, "$operatorText $operand failed")
        }
    }

    private fun <T> testOperatorOnLong(
        operator: JcUnaryOperator,
        operatorText: String,
        onLong: (Long) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) {
        longData.map { operand ->
            val expr = ctx.mkBv(operand)
            val result = operator(expr)

            val expected = try {
                onLong(operand)
            } catch (_: ArithmeticException) {
                null
            }
            val actual = extractFromUExpr(result)

            assertEquals(expected, actual, "$operatorText $operand failed")
        }
    }

    private fun <T> testOperatorOnFloat(
        operator: JcUnaryOperator,
        operatorText: String,
        onFloat: (Float) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) {
        floatData.map { operand ->
            val expr = ctx.mkFp32(operand)
            val result = operator(expr)

            val expected = try {
                onFloat(operand)
            } catch (_: ArithmeticException) {
                null
            }
            val actual = extractFromUExpr(result)

            assertEquals(expected, actual, "$operatorText $operand failed")
        }
    }

    private fun <T> testOperatorOnDouble(
        operator: JcUnaryOperator,
        operatorText: String,
        onFloat: (Double) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) {
        doubleData.map { operand ->
            val expr = ctx.mkFp64(operand)
            val result = operator(expr)

            val expected = try {
                onFloat(operand)
            } catch (_: ArithmeticException) {
                null
            }
            val actual = extractFromUExpr(result)

            assertEquals(expected, actual, "$operatorText $operand failed")
        }
    }
}