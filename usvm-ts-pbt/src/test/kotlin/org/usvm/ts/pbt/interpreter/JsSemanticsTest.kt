package org.usvm.ts.pbt.interpreter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsSemanticsTest {

    @Test
    fun truthiness() {
        assertFalse(JsSemantics.truthy(VNumber(0.0)))
        assertFalse(JsSemantics.truthy(VNumber(-0.0)))
        assertFalse(JsSemantics.truthy(VNumber(Double.NaN)))
        assertTrue(JsSemantics.truthy(VNumber(-1.5)))
        assertFalse(JsSemantics.truthy(VString("")))
        assertTrue(JsSemantics.truthy(VString("0")))
        assertFalse(JsSemantics.truthy(VNull))
        assertFalse(JsSemantics.truthy(VUndefined))
        assertTrue(JsSemantics.truthy(VObject(null)))
        assertTrue(JsSemantics.truthy(VArray())) // [] is truthy!
    }

    @Test
    fun toNumberCoercions() {
        assertEquals(0.0, JsSemantics.toNumber(VNull))
        assertTrue(JsSemantics.toNumber(VUndefined).isNaN())
        assertEquals(1.0, JsSemantics.toNumber(VBool(true)))
        assertEquals(0.0, JsSemantics.toNumber(VString("")))
        assertEquals(0.0, JsSemantics.toNumber(VString("   ")))
        assertEquals(42.0, JsSemantics.toNumber(VString(" 42 ")))
        assertEquals(255.0, JsSemantics.toNumber(VString("0xFF")))
        assertTrue(JsSemantics.toNumber(VString("12abc")).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, JsSemantics.toNumber(VString("Infinity")))
        // [] -> "" -> 0 ; [5] -> "5" -> 5 ; [1,2] -> "1,2" -> NaN
        assertEquals(0.0, JsSemantics.toNumber(VArray()))
        assertEquals(5.0, JsSemantics.toNumber(VArray(mutableListOf(VNumber(5.0)))))
        assertTrue(JsSemantics.toNumber(VArray(mutableListOf(VNumber(1.0), VNumber(2.0)))).isNaN())
        assertTrue(JsSemantics.toNumber(VObject(null)).isNaN())
    }

    @Test
    fun numberToStringMatchesJs() {
        assertEquals("1", JsSemantics.numberToString(1.0))
        assertEquals("0", JsSemantics.numberToString(-0.0))
        assertEquals("-7", JsSemantics.numberToString(-7.0))
        assertEquals("1.5", JsSemantics.numberToString(1.5))
        assertEquals("NaN", JsSemantics.numberToString(Double.NaN))
        assertEquals("Infinity", JsSemantics.numberToString(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", JsSemantics.numberToString(Double.NEGATIVE_INFINITY))
        assertEquals("100000", JsSemantics.numberToString(1e5))
    }

    @Test
    fun addOperator() {
        // number + string -> concatenation
        assertEquals(VString("12"), JsSemantics.add(VNumber(1.0), VString("2")))
        // undefined + 1 -> NaN
        val r = JsSemantics.add(VUndefined, VNumber(1.0))
        assertTrue((r as VNumber).value.isNaN())
        // null + 1 -> 1
        assertEquals(VNumber(1.0), JsSemantics.add(VNull, VNumber(1.0)))
        // [1] + [2] -> "12"
        assertEquals(
            VString("12"),
            JsSemantics.add(VArray(mutableListOf(VNumber(1.0))), VArray(mutableListOf(VNumber(2.0)))),
        )
        // {} + {} -> "[object Object][object Object]"
        assertEquals(
            VString("[object Object][object Object]"),
            JsSemantics.add(VObject(null), VObject(null)),
        )
    }

    @Test
    fun looseEquality() {
        assertTrue(JsSemantics.looseEq(VString(""), VNumber(0.0)))       // "" == 0
        assertTrue(JsSemantics.looseEq(VNull, VUndefined))               // null == undefined
        assertFalse(JsSemantics.looseEq(VNull, VNumber(0.0)))            // null != 0
        assertTrue(JsSemantics.looseEq(VBool(true), VNumber(1.0)))       // true == 1
        assertTrue(JsSemantics.looseEq(VString("1"), VBool(true)))       // "1" == true
        assertFalse(JsSemantics.looseEq(VNumber(Double.NaN), VNumber(Double.NaN)))
        // [0] == false (array -> "0" -> 0, false -> 0)
        assertTrue(JsSemantics.looseEq(VArray(mutableListOf(VNumber(0.0))), VBool(false)))
    }

    @Test
    fun strictEquality() {
        assertFalse(JsSemantics.strictEq(VNumber(Double.NaN), VNumber(Double.NaN)))
        assertTrue(JsSemantics.strictEq(VNumber(0.0), VNumber(-0.0)))
        assertFalse(JsSemantics.strictEq(VString("1"), VNumber(1.0)))
        val a = VArray()
        assertTrue(JsSemantics.strictEq(a, a))
        assertFalse(JsSemantics.strictEq(VArray(), VArray()))
    }

    @Test
    fun relationalOperators() {
        assertTrue(JsSemantics.ge(VNull, VNumber(0.0)))      // null >= 0 (!)
        assertFalse(JsSemantics.gt(VNull, VNumber(0.0)))     // null > 0 -> false
        assertFalse(JsSemantics.lt(VUndefined, VNumber(1.0))) // NaN comparison
        assertTrue(JsSemantics.lt(VString("a"), VString("b")))
        assertTrue(JsSemantics.lt(VString("10"), VString("9"))) // lexicographic!
        assertTrue(JsSemantics.lt(VString("10"), VNumber(90.0))) // numeric
    }

    @Test
    fun int32Conversions() {
        assertEquals(0, JsSemantics.toInt32(VNumber(Double.NaN)))
        assertEquals(0, JsSemantics.toInt32(VNumber(Double.POSITIVE_INFINITY)))
        assertEquals(-1, JsSemantics.toInt32(VNumber(4294967295.0))) // 2^32-1 -> -1
        assertEquals(1, JsSemantics.toInt32(VNumber(1.9)))
        assertEquals(-1, JsSemantics.toInt32(VNumber(-1.9)))
        assertEquals(4294967295L, JsSemantics.toUint32(VNumber(-1.0)))
    }

    @Test
    fun typeofOperator() {
        assertEquals("number", JsSemantics.typeOf(VNumber(1.0)))
        assertEquals("object", JsSemantics.typeOf(VNull))
        assertEquals("undefined", JsSemantics.typeOf(VUndefined))
        assertEquals("object", JsSemantics.typeOf(VArray()))
    }
}
