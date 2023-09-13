package org.usvm.machine.operator

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.extractBool
import org.usvm.machine.extractDouble
import org.usvm.machine.extractFloat
import org.usvm.machine.extractInt
import org.usvm.machine.extractLong
import kotlin.test.assertEquals

class JcBinaryOperatorTest {
    lateinit var ctx: JcContext

    @BeforeEach
    fun initializeContext() {
        ctx = JcContext(mockk(), mockk())
    }

    @TestFactory
    fun `Test addition`() =
        testOnAll(
            operator = JcBinaryOperator.Add,
            operatorText = "+",
            onBytes = Byte::plus,
            onChars = { a, b -> a.code + b.code },
            onShorts = Short::plus,
            onInts = Int::plus,
            onLongs = Long::plus,
            onFloats = Float::plus,
            onDoubles = Double::plus,
        )

    @TestFactory
    fun `Test subtraction`() =
        testOnAll(
            operator = JcBinaryOperator.Sub,
            operatorText = "-",
            onBytes = Byte::minus,
            onChars = { a, b -> a.code - b.code },
            onShorts = Short::minus,
            onInts = Int::minus,
            onLongs = Long::minus,
            onFloats = Float::minus,
            onDoubles = Double::minus,
        )

    @TestFactory
    fun `Test multiplication`() =
        testOnAll(
            operator = JcBinaryOperator.Mul,
            onBytes = Byte::times,
            onChars = { a, b -> a.code * b.code },
            onShorts = Short::times,
            operatorText = "*",
            onInts = Int::times,
            onLongs = Long::times,
            onFloats = Float::times,
            onDoubles = Double::times,
        )


    @Disabled("TODO: waiting for sympfu in KSMT")
    @TestFactory
    fun `Test remainder`() =
        testOnAll(
            operator = JcBinaryOperator.Rem,
            operatorText = "%",
            onBytes = Byte::rem,
            onChars = { a, b -> a.code % b.code },
            onShorts = Short::rem,
            onInts = Int::rem,
            onLongs = Long::rem,
            onFloats = Float::rem,
            onDoubles = Double::rem,
        )

    @TestFactory
    fun `Test division`() =
        testOnAll(
            operator = JcBinaryOperator.Div,
            operatorText = "/",
            onBytes = Byte::div,
            onChars = { a, b -> a.code / b.code },
            onShorts = Short::div,
            onInts = Int::div,
            onLongs = Long::div,
            onFloats = Float::div,
            onDoubles = Double::div,
        )

    @TestFactory
    fun `Test eq`() =
        testBoolOperatorOnAll(
            operator = JcBinaryOperator.Eq,
            operatorText = "==",
            onInts = Int::equals,
            onLongs = Long::equals,
            onFloats = { lhs, rhs -> lhs == rhs }, // nan special case
            onDoubles = { lhs, rhs -> lhs == rhs }, // nan special case
        )

    @TestFactory
    fun `Test neq`() =
        testBoolOperatorOnAll(
            operator = JcBinaryOperator.Neq,
            operatorText = "!=",
            onInts = { lhs, rhs -> lhs != rhs },
            onLongs = { lhs, rhs -> lhs != rhs },
            onFloats = { lhs, rhs -> lhs != rhs },
            onDoubles = { lhs, rhs -> lhs != rhs },
        )

    @TestFactory
    fun `Test lt`() =
        testBoolOperatorOnAll(
            operator = JcBinaryOperator.Lt,
            operatorText = "<",
            onInts = { lhs, rhs -> lhs < rhs },
            onLongs = { lhs, rhs -> lhs < rhs },
            onFloats = { lhs, rhs -> lhs < rhs },
            onDoubles = { lhs, rhs -> lhs < rhs },
        )

    @TestFactory
    fun `Test le`() =
        testBoolOperatorOnAll(
            operator = JcBinaryOperator.Le,
            operatorText = "<=",
            onInts = { lhs, rhs -> lhs <= rhs },
            onLongs = { lhs, rhs -> lhs <= rhs },
            onFloats = { lhs, rhs -> lhs <= rhs },
            onDoubles = { lhs, rhs -> lhs <= rhs },
        )

    @TestFactory
    fun `Test gt`() =
        testBoolOperatorOnAll(
            operator = JcBinaryOperator.Gt,
            operatorText = ">",
            onInts = { lhs, rhs -> lhs > rhs },
            onLongs = { lhs, rhs -> lhs > rhs },
            onFloats = { lhs, rhs -> lhs > rhs },
            onDoubles = { lhs, rhs -> lhs > rhs },
        )

    @TestFactory
    fun `Test ge`() =
        testBoolOperatorOnAll(
            operator = JcBinaryOperator.Ge,
            operatorText = ">=",
            onInts = { lhs, rhs -> lhs >= rhs },
            onLongs = { lhs, rhs -> lhs >= rhs },
            onFloats = { lhs, rhs -> lhs >= rhs },
            onDoubles = { lhs, rhs -> lhs >= rhs },
        )

    @TestFactory
    fun `Test and`() =
        listOf(
            testOperatorOnInts(
                operator = JcBinaryOperator.And,
                operatorText = "&",
                onInts = { lhs, rhs -> lhs and rhs },
                ::extractInt
            )
        )

    @TestFactory
    fun `Test or`() =
        listOf(
            testOperatorOnInts(
                operator = JcBinaryOperator.Or,
                operatorText = "|",
                onInts = { lhs, rhs -> lhs or rhs },
                ::extractInt
            )
        )

    @TestFactory
    fun `Test xor`() =
        listOf(
            testOperatorOnInts(
                operator = JcBinaryOperator.Xor,
                operatorText = "^",
                onInts = { lhs, rhs -> lhs xor rhs },
                ::extractInt
            )
        )

    @TestFactory
    fun `Test cmp`() =
        listOf(
            testOperatorOnLongs(
                operator = JcBinaryOperator.Cmp,
                operatorText = "cmp",
                onLongs = Long::compareTo,
                ::extractInt,
            )
        )

    @TestFactory
    fun `Test cmpl`() =
        listOf(
            testOperatorOnFloats(
                operator = JcBinaryOperator.Cmpl,
                operatorText = "cmpl",
                onFloats = { lhs, rhs -> if (lhs.isNaN() || rhs.isNaN()) -1 else compareFloats(lhs, rhs) },
                ::extractInt,
            )
        )

    @TestFactory
    fun `Test cmpg`() =
        listOf(
            testOperatorOnFloats(
                operator = JcBinaryOperator.Cmpg,
                operatorText = "cmpg",
                onFloats = { lhs, rhs -> if (lhs.isNaN() || rhs.isNaN()) 1 else compareFloats(lhs, rhs) },
                ::extractInt,
            )
        )

    /**
     * According to the specification, 0.0 and -0.0 are equal,
     * but according to the Float.compareTo -0.0 < 0.0.
     * */
    private fun compareFloats(lhs: Float, rhs: Float): Int {
        check(!lhs.isNaN() && !rhs.isNaN())
        if (lhs == 0.0f && rhs == 0.0f) {
            return 0;
        }
        return lhs.compareTo(rhs)
    }


    private fun testOnAll(
        operator: JcBinaryOperator,
        operatorText: String,
        onBytes: (Byte, Byte) -> Int,
        onChars: (Char, Char) -> Int,
        onShorts: (Short, Short) -> Int,
        onInts: (Int, Int) -> Int,
        onLongs: (Long, Long) -> Long,
        onFloats: (Float, Float) -> Float,
        onDoubles: (Double, Double) -> Double,
    ) = listOf(
        testOperatorOnBytes(
            operator,
            operatorText,
            onBytes,
            ::extractInt
        ),
        testOperatorOnChars(
            operator,
            operatorText,
            onChars,
            ::extractInt
        ),
        testOperatorOnShorts(
            operator,
            operatorText,
            onShorts,
            ::extractInt
        ),
        testOperatorOnInts(
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
        operator: JcBinaryOperator,
        operatorText: String,
        onInts: (Int, Int) -> Boolean,
        onLongs: (Long, Long) -> Boolean,
        onFloats: (Float, Float) -> Boolean,
        onDoubles: (Double, Double) -> Boolean,
    ) = listOf(
        testOperatorOnInts(
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

    private fun <T> testOperatorOnBytes(
        operator: JcBinaryOperator,
        operatorText: String,
        onBytes: (Byte, Byte) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Byte $operatorText Byte") {
        testOperator(
            operator,
            operatorText,
            onBytes,
            extractFromUExpr,
            { ctx.mkBv(it, ctx.byteSort).wideTo32BitsIfNeeded(true) },
            byteData
        )
    }

    private fun <T> testOperatorOnChars(
        operator: JcBinaryOperator,
        operatorText: String,
        onChars: (Char, Char) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Char $operatorText Char") {
        testOperator(
            operator,
            operatorText,
            onChars,
            extractFromUExpr,
            { ctx.mkBv(it.code, ctx.charSort).wideTo32BitsIfNeeded(false) },
            charData
        )
    }

    private fun <T> testOperatorOnShorts(
        operator: JcBinaryOperator,
        operatorText: String,
        onShorts: (Short, Short) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Short $operatorText Short") {
        testOperator(
            operator,
            operatorText,
            onShorts,
            extractFromUExpr,
            { ctx.mkBv(it, ctx.shortSort).wideTo32BitsIfNeeded(true) },
            shortData
        )
    }

    private fun <T> testOperatorOnInts(
        operator: JcBinaryOperator,
        operatorText: String,
        onInts: (Int, Int) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Int $operatorText Int") {
        testOperator(
            operator,
            operatorText,
            onInts,
            extractFromUExpr,
            ctx::mkBv,
            intData
        )
    }

    private fun <T> testOperatorOnLongs(
        operator: JcBinaryOperator,
        operatorText: String,
        onLongs: (Long, Long) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Long $operatorText Long") {
        testOperator(
            operator,
            operatorText,
            onLongs,
            extractFromUExpr,
            ctx::mkBv,
            longData
        )
    }

    private fun <T> testOperatorOnFloats(
        operator: JcBinaryOperator,
        operatorText: String,
        onFloats: (Float, Float) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Float $operatorText Float") {
        testOperator(
            operator,
            operatorText,
            onFloats,
            extractFromUExpr,
            ctx::mkFp32,
            floatData
        )
    }

    private fun <T> testOperatorOnDoubles(
        operator: JcBinaryOperator,
        operatorText: String,
        onDoubles: (Double, Double) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
    ) = DynamicTest.dynamicTest("Double $operatorText Double") {
        testOperator(
            operator,
            operatorText,
            onDoubles,
            extractFromUExpr,
            ctx::mkFp64,
            doubleData
        )
    }

    private fun <Primitive, T> testOperator(
        operator: JcBinaryOperator,
        operatorText: String,
        onPrimitives: (Primitive, Primitive) -> T,
        extractFromUExpr: (UExpr<out USort>) -> T?,
        makeExpr: (Primitive) -> UExpr<out USort>,
        data: List<Primitive>,
    ) {
        data.flatMap { lhs ->
            data.map { rhs ->
                val expected = try {
                    onPrimitives(lhs, rhs)
                } catch (_: ArithmeticException) {
                    null // useful for division by zero
                }

                val exprLhs = makeExpr(lhs)
                val exprRhs = makeExpr(rhs)
                val actual = extractFromUExpr(operator(exprLhs, exprRhs))

                assertEquals(expected, actual, "$lhs $operatorText $rhs failed")
            }
        }
    }
}
