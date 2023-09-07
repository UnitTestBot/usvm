package org.usvm.machine.operators

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.extractByte
import org.usvm.machine.extractChar
import org.usvm.machine.extractDouble
import org.usvm.machine.extractFloat
import org.usvm.machine.extractInt
import org.usvm.machine.extractLong
import org.usvm.machine.extractShort
import kotlin.test.assertEquals

class JcUnaryOperatorTest {
    lateinit var ctx: JcContext

    @BeforeEach
    fun initializeContext() {
        ctx = JcContext(mockk(), mockk())
    }

    @Test
    fun `Test cast byte to char`() =
        testOperator(
            JcUnaryOperator.CastToChar,
            "(char)",
            { it.toInt().toChar() },
            ::extractChar,
            ctx::mkBv,
            byteData,
        )

    @Test
    fun `Test cast byte to short`() =
        testOperator(
            JcUnaryOperator.CastToShort,
            "(short)",
            { it.toInt().toShort() },
            ::extractShort,
            ctx::mkBv,
            byteData,
        )

    @Test
    fun `Test cast byte to int`() =
        testOperator(
            JcUnaryOperator.CastToInt,
            "(int)",
            Byte::toInt,
            ::extractInt,
            ctx::mkBv,
            byteData,
        )

    @Test
    fun `Test cast char to int`() =
        testOperator(
            JcUnaryOperator.CastToInt,
            "(int)",
            Char::code,
            ::extractInt,
            { operand -> ctx.mkBv(operand.code, ctx.charSort).wideTo32BitsIfNeeded(false) },
            charData,
        )

    @Test
    fun `Test cast short to int`() =
        testOperator(
            JcUnaryOperator.CastToInt,
            "(int)",
            Short::toInt,
            ::extractInt,
            ctx::mkBv,
            shortData,
        )

    @Test
    fun `Test cast int to byte`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToByte,
            "(byte)",
            Int::toByte,
            ::extractByte
        )

    @Test
    fun `Test cast int to char`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToChar,
            "(char)",
            Int::toChar,
            ::extractChar
        )

    @Test
    fun `Test cast int to short`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToShort,
            "(short)",
            Int::toShort,
            ::extractShort
        )

    @Test
    fun `Test cast int to long`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToLong,
            "(long)",
            Int::toLong,
            ::extractLong
        )

    @Test
    fun `Test cast int to float`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToFloat,
            "(float)",
            Int::toFloat,
            ::extractFloat
        )

    @Test
    fun `Test cast int to double`() =
        testOperatorOnInt(
            JcUnaryOperator.CastToDouble,
            "(double)",
            Int::toDouble,
            ::extractDouble
        )

    @Test
    fun `Test cast long to int`() =
        testOperatorOnLong(
            JcUnaryOperator.CastToInt,
            "(int)",
            Long::toInt,
            ::extractInt
        )

    @Test
    fun `Test cast long to float`() =
        testOperatorOnLong(
            JcUnaryOperator.CastToFloat,
            "(float)",
            Long::toFloat,
            ::extractFloat
        )


    @Test
    fun `Test cast long to double`() =
        testOperatorOnLong(
            JcUnaryOperator.CastToDouble,
            "(double)",
            Long::toDouble,
            ::extractDouble
        )

    @Test
    @Disabled("TODO: fix conversion of float to int")
    fun `Test cast float to int`() =
        testOperatorOnFloat(
            JcUnaryOperator.CastToInt,
            "(int)",
            Float::toInt,
            ::extractInt
        )

    @Test
    fun `Test cast float to long`() =
        testOperatorOnFloat(
            JcUnaryOperator.CastToLong,
            "(long)",
            Float::toLong,
            ::extractLong
        )


    @Test
    fun `Test cast float to double`() =
        testOperatorOnFloat(
            JcUnaryOperator.CastToDouble,
            "(double)",
            Float::toDouble,
            ::extractDouble
        )

    @Test
    fun `Test cast double to int`() =
        testOperatorOnDouble(
            JcUnaryOperator.CastToInt,
            "(int)",
            Double::toInt,
            ::extractInt
        )

    @Test
    fun `Test cast double to long`() =
        testOperatorOnDouble(
            JcUnaryOperator.CastToLong,
            "(long)",
            Double::toLong,
            ::extractLong
        )


    @Test
    fun `Test cast double to float`() =
        testOperatorOnDouble(
            JcUnaryOperator.CastToFloat,
            "(float)",
            Double::toFloat,
            ::extractFloat
        )

    private fun <T> testOperatorOnInt(
        operator: JcUnaryOperator,
        operatorText: String,
        onInt: (Int) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = testOperator(
        operator,
        operatorText,
        onInt,
        extractFromUExpr,
        ctx::mkBv,
        intData
    )

    private fun <T> testOperatorOnLong(
        operator: JcUnaryOperator,
        operatorText: String,
        onLong: (Long) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = testOperator(
        operator,
        operatorText,
        onLong,
        extractFromUExpr,
        ctx::mkBv,
        longData
    )

    private fun <T> testOperatorOnFloat(
        operator: JcUnaryOperator,
        operatorText: String,
        onFloat: (Float) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = testOperator(
        operator,
        operatorText,
        onFloat,
        extractFromUExpr,
        ctx::mkFp32,
        floatData
    )

    private fun <T> testOperatorOnDouble(
        operator: JcUnaryOperator,
        operatorText: String,
        onDouble: (Double) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = testOperator(
        operator,
        operatorText,
        onDouble,
        extractFromUExpr,
        ctx::mkFp64,
        doubleData
    )

    private fun <Primitive, T> testOperator(
        operator: JcUnaryOperator,
        operatorText: String,
        onPrimitive: (Primitive) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
        makeExpr: (Primitive) -> UExpr<out USort>,
        data: List<Primitive>,
    ) {
        data.map { operand ->
            val expected = onPrimitive(operand)

            val expr = makeExpr(operand)
            val actual = extractFromUExpr(operator(expr))

            assertEquals(expected, actual, "$operatorText $operand failed")
        }
    }
}