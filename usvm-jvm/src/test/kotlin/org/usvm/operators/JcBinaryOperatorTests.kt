package org.usvm.operators

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFalse
import org.usvm.USort
import org.usvm.UTrue
import org.usvm.operator.JcBinOperator
import kotlin.test.assertEquals

class JcBinaryOperatorTests {
    lateinit var ctx: UContext

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
    }

    @TestFactory
    fun `Test addition`() =
        testOnAll(
            operator = JcBinOperator.Add,
            operatorText = "+",
            onInts = Int::plus,
            onLongs = Long::plus,
            onFloats = Float::plus,
            onDoubles = Double::plus,
        )

    @TestFactory
    fun `Test subtraction`() =
        testOnAll(
            operator = JcBinOperator.Sub,
            operatorText = "-",
            onInts = Int::minus,
            onLongs = Long::minus,
            onFloats = Float::minus,
            onDoubles = Double::minus,
        )

    @TestFactory
    fun `Test multiplication`() =
        testOnAll(
            operator = JcBinOperator.Mul,
            operatorText = "+",
            onInts = Int::times,
            onLongs = Long::times,
            onFloats = Float::times,
            onDoubles = Double::times,
        )


    @Disabled("TODO: waiting for sympfu in KSMT")
    @TestFactory
    fun `Test remainder`() =
        testOnAll(
            operator = JcBinOperator.Rem,
            operatorText = "%",
            onInts = Int::rem,
            onLongs = Long::rem,
            onFloats = Float::rem,
            onDoubles = Double::rem,
        )

    @TestFactory
    fun `Test eq`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Eq,
            operatorText = "==",
            onInts = Int::equals,
            onLongs = Long::equals,
            onFloats = { a, b -> a == b }, // nan special case
            onDoubles = { a, b -> a == b }, // nan special case
        )

    @TestFactory
    fun `Test neq`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Neq,
            operatorText = "==",
            onInts = { a, b -> a != b },
            onLongs = { a, b -> a != b },
            onFloats = { a, b -> a != b },
            onDoubles = { a, b -> a != b },
        )

    @TestFactory
    fun `Test lt`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Lt,
            operatorText = "<",
            onInts = { a, b -> a < b },
            onLongs = { a, b -> a < b },
            onFloats = { a, b -> a < b },
            onDoubles = { a, b -> a < b },
        )

    @TestFactory
    fun `Test le`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Le,
            operatorText = "<=",
            onInts = { a, b -> a <= b },
            onLongs = { a, b -> a <= b },
            onFloats = { a, b -> a <= b },
            onDoubles = { a, b -> a <= b },
        )

    @TestFactory
    fun `Test gt`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Gt,
            operatorText = ">",
            onInts = { a, b -> a > b },
            onLongs = { a, b -> a > b },
            onFloats = { a, b -> a > b },
            onDoubles = { a, b -> a > b },
        )

    @TestFactory
    fun `Test ge`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Ge,
            operatorText = ">=",
            onInts = { a, b -> a >= b },
            onLongs = { a, b -> a >= b },
            onFloats = { a, b -> a >= b },
            onDoubles = { a, b -> a >= b },
        )

    @TestFactory
    fun `Test and`() =
        listOf(
            testOperatorOnIntegers(
                operator = JcBinOperator.And,
                operatorText = "&",
                onInts = { a, b -> a and b },
                ::extractInt,
                intData
            )
        )

    @TestFactory
    fun `Test or`() =
        listOf(
            testOperatorOnIntegers(
                operator = JcBinOperator.Or,
                operatorText = "|",
                onInts = { a, b -> a or b },
                ::extractInt,
                intData
            )
        )

    @TestFactory
    fun `Test xor`() =
        listOf(
            testOperatorOnIntegers(
                operator = JcBinOperator.Xor,
                operatorText = "^",
                onInts = { a, b -> a xor b },
                ::extractInt,
                intData
            )
        )

    @TestFactory
    fun `Test division`() =
        testOnAll(
            operator = JcBinOperator.Div,
            operatorText = "/",
            onInts = Int::div,
            onLongs = Long::div,
            onFloats = Float::div,
            onDoubles = Double::div,
        )

    @TestFactory
    fun `Test cmp`() =
        listOf(
            testOperatorOnLongs(
                operator = JcBinOperator.Cmp,
                operatorText = "cmp",
                onLongs = Long::compareTo,
                ::extractLong,
                longData
            )
        )

    @TestFactory
    fun `Test cmpl`() =
        listOf(
            testOperatorOnFloats(
                operator = JcBinOperator.Cmpl,
                operatorText = "cmpl",
                onFloats = { a, b -> if (a.isNaN() || b.isNaN()) -1 else a.compareTo(b) },
                ::extractInt,
                floatData,
            )
        )

    @TestFactory
    fun `Test cmpg`() =
        listOf(
            testOperatorOnFloats(
                operator = JcBinOperator.Cmpg,
                operatorText = "cmpg",
                onFloats = { a, b -> if (a.isNaN() || b.isNaN()) 1 else a.compareTo(b) },
                ::extractInt,
                floatData,
            )
        )


    private fun testOnAll(
        operator: JcBinOperator,
        operatorText: String,
        onInts: (Int, Int) -> Int,
        onLongs: (Long, Long) -> Long,
        onFloats: (Float, Float) -> Float,
        onDoubles: (Double, Double) -> Double,
    ) = listOf(
        testOperatorOnIntegers(
            operator,
            operatorText,
            onInts,
            ::extractInt,
            intData
        ),
        testOperatorOnLongs(
            operator,
            operatorText,
            onLongs,
            ::extractLong,
            longData
        ),
        testOperatorOnFloats(
            operator,
            operatorText,
            onFloats,
            ::extractFloat,
            floatData
        ),
        testOperatorOnDoubles(
            operator,
            operatorText,
            onDoubles,
            ::extractDouble,
            doubleData
        )
    )

    private fun testBoolOperatorOnAll(
        operator: JcBinOperator,
        operatorText: String,
        onInts: (Int, Int) -> Boolean,
        onLongs: (Long, Long) -> Boolean,
        onFloats: (Float, Float) -> Boolean,
        onDoubles: (Double, Double) -> Boolean,
    ) = listOf(
        testOperatorOnIntegers(
            operator,
            operatorText,
            onInts,
            ::extractBool,
            intData
        ),
        testOperatorOnLongs(
            operator,
            operatorText,
            onLongs,
            ::extractBool,
            longData
        ),
        testOperatorOnFloats(
            operator,
            operatorText,
            onFloats,
            ::extractBool,
            floatData
        ),
        testOperatorOnDoubles(
            operator,
            operatorText,
            onDoubles,
            ::extractBool,
            doubleData
        )
    )

    private fun <T> testOperatorOnIntegers(
        operator: JcBinOperator,
        operatorText: String,
        onInts: (Int, Int) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
        data: List<Int>,
    ) = DynamicTest.dynamicTest("Int $operatorText Int") {
        data.flatMap { a ->
            data.map { b ->

                val exprA = ctx.mkBv(a)
                val exprB = ctx.mkBv(b)
                val result = operator(exprA, exprB)

                val expected = try {
                    onInts(a, b)
                } catch (_: ArithmeticException) {
                    null
                }

                val actual = extractFromUExpr(result)

                assertEquals(expected, actual, "$a $operatorText $b failed")
            }
        }
    }

    private fun <T> testOperatorOnLongs(
        operator: JcBinOperator,
        operatorText: String,
        onLongs: (Long, Long) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
        data: List<Long>,
    ) = DynamicTest.dynamicTest("Long $operatorText Long") {
        data.flatMap { a ->
            data.map { b ->
                {
                    val exprA = ctx.mkBv(a)
                    val exprB = ctx.mkBv(b)
                    val result = operator(exprA, exprB)

                    val expected = try {
                        onLongs(a, b)
                    } catch (_: ArithmeticException) {
                        null
                    }

                    val actual = extractFromUExpr(result)

                    assertEquals(expected, actual, "$a $operatorText $b failed")
                }
            }
        }
    }

    private fun <T> testOperatorOnFloats(
        operator: JcBinOperator,
        operatorText: String,
        onFloats: (Float, Float) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
        data: List<Float>,
    ) = DynamicTest.dynamicTest("Float $operatorText Float") {
        data.flatMap { a ->
            data.map { b ->
                val exprA = ctx.mkFp32(a)
                val exprB = ctx.mkFp32(b)
                val result = operator(exprA, exprB)

                val expected = try {
                    onFloats(a, b)
                } catch (_: ArithmeticException) {
                    null
                }
                val actual = extractFromUExpr(result)

                assertEquals(expected, actual, "$a $operatorText $b failed")
            }
        }
    }

    private fun <T> testOperatorOnDoubles(
        operator: JcBinOperator,
        operatorText: String,
        onDoubles: (Double, Double) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
        data: List<Double>,
    ) = DynamicTest.dynamicTest("Double $operatorText Double") {
        data.flatMap { a ->
            data.map { b ->
                val exprA = ctx.mkFp64(a)
                val exprB = ctx.mkFp64(b)
                val result = operator(exprA, exprB)

                val expected = try {
                    onDoubles(a, b)
                } catch (_: ArithmeticException) {
                    null
                }
                val actual = extractFromUExpr(result)

                assertEquals(expected, actual, "$a $operatorText $b failed")
            }
        }
    }


    companion object {

        private fun extractBool(expr: UExpr<out USort>): Boolean? = when (expr) {
            is UTrue -> true
            is UFalse -> false
            else -> null
        }

        private fun extractInt(expr: UExpr<out USort>): Int? = (expr as? KBitVec32Value)?.intValue

        private fun extractLong(expr: UExpr<out USort>): Long? = (expr as? KBitVec64Value)?.longValue

        private fun extractFloat(expr: UExpr<out USort>): Float? = (expr as? KFp32Value)?.value

        private fun extractDouble(expr: UExpr<out USort>): Double? = (expr as? KFp64Value)?.value


        private val intData = listOf(
            0,
            1,
            -1,
            2,
            -2,
            100_500,
            -100_500,
            1337,
            -1337,
            1_000_000_000,
            -1_000_000_000,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
        )
        private val longData = listOf(
            0L,
            1L,
            -1L,
            2L,
            -2L,
            100_500L,
            -100_500L,
            1337L,
            -1337L,
            1e18.toLong(),
            (-1e18).toLong(),
            Long.MIN_VALUE,
            Long.MAX_VALUE,
        )
        private val floatData = listOf(
            0f,
            1f,
            -1f,
            2f,
            -2f,
            100_500f,
            -100_500f,
            1337f,
            -1337f,
            1_000_000_000f,
            -1_000_000_000f,
            1e18.toFloat(),
            (-1e18).toFloat(),
            Float.MIN_VALUE,
            Float.MAX_VALUE,
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
        )
        private val doubleData = listOf(
            0.0,
            1.0,
            -1.0,
            2.0,
            -2.0,
            100_500.0,
            -100_500.0,
            1337.0,
            -1337.0,
            1_000_000_000.0,
            -1_000_000_000.0,
            Double.MIN_VALUE,
            Double.MAX_VALUE,
            Double.NaN,
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY,
        )
    }
}
