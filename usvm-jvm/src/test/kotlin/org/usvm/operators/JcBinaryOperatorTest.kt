package org.usvm.operators

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.operator.JcBinOperator
import kotlin.test.assertEquals

class JcBinaryOperatorTest {
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
            onFloats = { lhs, rhs -> lhs == rhs }, // nan special case
            onDoubles = { lhs, rhs -> lhs == rhs }, // nan special case
        )

    @TestFactory
    fun `Test neq`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Neq,
            operatorText = "==",
            onInts = { lhs, rhs -> lhs != rhs },
            onLongs = { lhs, rhs -> lhs != rhs },
            onFloats = { lhs, rhs -> lhs != rhs },
            onDoubles = { lhs, rhs -> lhs != rhs },
        )

    @TestFactory
    fun `Test lt`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Lt,
            operatorText = "<",
            onInts = { lhs, rhs -> lhs < rhs },
            onLongs = { lhs, rhs -> lhs < rhs },
            onFloats = { lhs, rhs -> lhs < rhs },
            onDoubles = { lhs, rhs -> lhs < rhs },
        )

    @TestFactory
    fun `Test le`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Le,
            operatorText = "<=",
            onInts = { lhs, rhs -> lhs <= rhs },
            onLongs = { lhs, rhs -> lhs <= rhs },
            onFloats = { lhs, rhs -> lhs <= rhs },
            onDoubles = { lhs, rhs -> lhs <= rhs },
        )

    @TestFactory
    fun `Test gt`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Gt,
            operatorText = ">",
            onInts = { lhs, rhs -> lhs > rhs },
            onLongs = { lhs, rhs -> lhs > rhs },
            onFloats = { lhs, rhs -> lhs > rhs },
            onDoubles = { lhs, rhs -> lhs > rhs },
        )

    @TestFactory
    fun `Test ge`() =
        testBoolOperatorOnAll(
            operator = JcBinOperator.Ge,
            operatorText = ">=",
            onInts = { lhs, rhs -> lhs >= rhs },
            onLongs = { lhs, rhs -> lhs >= rhs },
            onFloats = { lhs, rhs -> lhs >= rhs },
            onDoubles = { lhs, rhs -> lhs >= rhs },
        )

    @TestFactory
    fun `Test and`() =
        listOf(
            testOperatorOnIntegers(
                operator = JcBinOperator.And,
                operatorText = "&",
                onInts = { lhs, rhs -> lhs and rhs },
                ::extractInt
            )
        )

    @TestFactory
    fun `Test or`() =
        listOf(
            testOperatorOnIntegers(
                operator = JcBinOperator.Or,
                operatorText = "|",
                onInts = { lhs, rhs -> lhs or rhs },
                ::extractInt
            )
        )

    @TestFactory
    fun `Test xor`() =
        listOf(
            testOperatorOnIntegers(
                operator = JcBinOperator.Xor,
                operatorText = "^",
                onInts = { lhs, rhs -> lhs xor rhs },
                ::extractInt
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
            )
        )

    @TestFactory
    fun `Test cmpl`() =
        listOf(
            testOperatorOnFloats(
                operator = JcBinOperator.Cmpl,
                operatorText = "cmpl",
                onFloats = { lhs, rhs -> if (lhs.isNaN() || rhs.isNaN()) -1 else lhs.compareTo(rhs) },
                ::extractInt,
            )
        )

    @TestFactory
    fun `Test cmpg`() =
        listOf(
            testOperatorOnFloats(
                operator = JcBinOperator.Cmpg,
                operatorText = "cmpg",
                onFloats = { lhs, rhs -> if (lhs.isNaN() || rhs.isNaN()) 1 else lhs.compareTo(rhs) },
                ::extractInt,
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
            ::extractInt
        ),
        testOperatorOnLongs(
            operator,
            operatorText,
            onLongs,
            ::extractLong,
        ),
        testOperatorOnFloats(
            operator,
            operatorText,
            onFloats,
            ::extractFloat,
        ),
        testOperatorOnDoubles(
            operator,
            operatorText,
            onDoubles,
            ::extractDouble,
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
            ::extractBool
        ),
        testOperatorOnLongs(
            operator,
            operatorText,
            onLongs,
            ::extractBool,
        ),
        testOperatorOnFloats(
            operator,
            operatorText,
            onFloats,
            ::extractBool,
        ),
        testOperatorOnDoubles(
            operator,
            operatorText,
            onDoubles,
            ::extractBool,
        )
    )

    private fun <T> testOperatorOnIntegers(
        operator: JcBinOperator,
        operatorText: String,
        onInts: (Int, Int) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Int $operatorText Int") {
        intData.flatMap { lhs ->
            intData.map { rhs ->

                val exprLhs = ctx.mkBv(lhs)
                val exprRhs = ctx.mkBv(rhs)
                val result = operator(exprLhs, exprRhs)

                val expected = try {
                    onInts(lhs, rhs)
                } catch (_: ArithmeticException) {
                    null
                }

                val actual = extractFromUExpr(result)

                assertEquals(expected, actual, "$lhs $operatorText $rhs failed")
            }
        }
    }

    private fun <T> testOperatorOnLongs(
        operator: JcBinOperator,
        operatorText: String,
        onLongs: (Long, Long) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Long $operatorText Long") {
        longData.flatMap { lhs ->
            longData.map { rhs ->
                {
                    val exprLhs = ctx.mkBv(lhs)
                    val exprRhs = ctx.mkBv(rhs)
                    val result = operator(exprLhs, exprRhs)

                    val expected = try {
                        onLongs(lhs, rhs)
                    } catch (_: ArithmeticException) {
                        null
                    }

                    val actual = extractFromUExpr(result)

                    assertEquals(expected, actual, "$lhs $operatorText $rhs failed")
                }
            }
        }
    }

    private fun <T> testOperatorOnFloats(
        operator: JcBinOperator,
        operatorText: String,
        onFloats: (Float, Float) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Float $operatorText Float") {
        floatData.flatMap { lhs ->
            floatData.map { rhs ->
                val exprLhs = ctx.mkFp32(lhs)
                val exprRhs = ctx.mkFp32(rhs)
                val result = operator(exprLhs, exprRhs)

                val expected = try {
                    onFloats(lhs, rhs)
                } catch (_: ArithmeticException) {
                    null
                }
                val actual = extractFromUExpr(result)

                assertEquals(expected, actual, "$lhs $operatorText $rhs failed")
            }
        }
    }

    private fun <T> testOperatorOnDoubles(
        operator: JcBinOperator,
        operatorText: String,
        onDoubles: (Double, Double) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Double $operatorText Double") {
        doubleData.flatMap { lhs ->
            doubleData.map { rhs ->
                val exprLhs = ctx.mkFp64(lhs)
                val exprRhs = ctx.mkFp64(rhs)
                val result = operator(exprLhs, exprRhs)

                val expected = try {
                    onDoubles(lhs, rhs)
                } catch (_: ArithmeticException) {
                    null
                }
                val actual = extractFromUExpr(result)

                assertEquals(expected, actual, "$lhs $operatorText $rhs failed")
            }
        }
    }
}
