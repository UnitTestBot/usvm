package org.usvm.ts.pbt.interpreter

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.truncate

/**
 * Concrete JS (ECMAScript) semantics for the values of [VValue].
 *
 * The symbolic counterparts (and the source of truth for what usvm-ts models) are
 * `org.usvm.machine.operator.TsBinaryOperator`, `TsUnaryOperator` and `mkTruthyExpr`.
 * Divergences between this implementation and the symbolic one are caught by the
 * differential test suite and whitelisted explicitly.
 */
object JsSemantics {

    // ToBoolean
    fun truthy(v: VValue): Boolean = when (v) {
        is VBool -> v.value
        is VNumber -> v.value != 0.0 && !v.value.isNaN()
        is VString -> v.value.isNotEmpty()
        VNull, VUndefined -> false
        is VObject, is VArray, is VNamespace, is VFunction, is VMap, is VSet -> true
    }

    // ToNumber
    fun toNumber(v: VValue): Double = when (v) {
        is VNumber -> v.value
        is VBool -> if (v.value) 1.0 else 0.0
        is VString -> stringToNumber(v.value)
        VNull -> 0.0
        VUndefined -> Double.NaN
        is VObject, is VArray, is VNamespace, is VFunction, is VMap, is VSet -> toNumber(toPrimitive(v))
    }

    fun stringToNumber(s: String): Double {
        val t = s.trim()
        if (t.isEmpty()) return 0.0
        return when {
            t == "Infinity" || t == "+Infinity" -> Double.POSITIVE_INFINITY
            t == "-Infinity" -> Double.NEGATIVE_INFINITY
            t.startsWith("0x") || t.startsWith("0X") ->
                t.substring(2).toLongOrNull(16)?.toDouble() ?: Double.NaN

            t.startsWith("0o") || t.startsWith("0O") ->
                t.substring(2).toLongOrNull(8)?.toDouble() ?: Double.NaN

            t.startsWith("0b") || t.startsWith("0B") ->
                t.substring(2).toLongOrNull(2)?.toDouble() ?: Double.NaN

            else -> t.toDoubleOrNull() ?: Double.NaN
        }
    }

    // ToString
    fun toStringJs(v: VValue): String = when (v) {
        is VString -> v.value
        is VNumber -> numberToString(v.value)
        is VBool -> v.value.toString()
        VNull -> "null"
        VUndefined -> "undefined"
        is VArray -> v.elements.joinToString(",") { el ->
            if (el == VNull || el == VUndefined) "" else toStringJs(el)
        }

        is VObject, is VNamespace, is VMap, is VSet -> "[object Object]"

        is VFunction -> "function ${v.method.name}() { [code] }"
    }

    /**
     * JS `Number::toString` approximation. Exact for integral values with |x| < 2^53
     * and the special values; for other doubles falls back to Kotlin's shortest
     * representation, which matches JS in the common range but differs in exponent
     * formatting corner cases (whitelisted in the differential suite).
     */
    fun numberToString(d: Double): String = when {
        d.isNaN() -> "NaN"
        d == Double.POSITIVE_INFINITY -> "Infinity"
        d == Double.NEGATIVE_INFINITY -> "-Infinity"
        d == 0.0 -> "0" // covers -0.0 as well
        d == floor(d) && abs(d) < 1e21 -> {
            if (abs(d) <= 9.007199254740992E15) d.toLong().toString()
            else java.math.BigDecimal(d).toBigInteger().toString()
        }

        else -> d.toString()
    }

    // ToPrimitive (hint: number/default). Plain objects have no user-defined valueOf in our model.
    fun toPrimitive(v: VValue): VValue = when (v) {
        is VArray -> VString(toStringJs(v))
        is VObject, is VNamespace, is VFunction, is VMap, is VSet -> VString(toStringJs(v))
        else -> v
    }

    // ToInt32 / ToUint32
    fun toInt32(v: VValue): Int {
        val d = toNumber(v)
        if (d.isNaN() || d.isInfinite()) return 0
        return truncate(d).toLong().toInt()
    }

    fun toUint32(v: VValue): Long {
        return toInt32(v).toLong() and 0xFFFFFFFFL
    }

    // Addition: string concatenation if either primitive is a string
    fun add(a: VValue, b: VValue): VValue {
        val pa = toPrimitive(a)
        val pb = toPrimitive(b)
        return if (pa is VString || pb is VString) {
            VString(toStringJs(pa) + toStringJs(pb))
        } else {
            VNumber(toNumber(pa) + toNumber(pb))
        }
    }

    // Strict equality (===)
    fun strictEq(a: VValue, b: VValue): Boolean = when {
        a is VNumber && b is VNumber -> a.value == b.value // NaN != NaN, +0 == -0
        a is VString && b is VString -> a.value == b.value
        a is VBool && b is VBool -> a.value == b.value
        a == VNull && b == VNull -> true
        a == VUndefined && b == VUndefined -> true
        a is VObject && b is VObject -> a === b
        a is VArray && b is VArray -> a === b
        a is VNamespace && b is VNamespace -> a == b
        a is VFunction && b is VFunction -> a === b
        a is VMap && b is VMap -> a === b
        a is VSet && b is VSet -> a === b
        else -> false
    }

    // Loose equality (==), ES2015 7.2.12
    fun looseEq(a: VValue, b: VValue): Boolean = when {
        sameTypeCategory(a, b) -> strictEq(a, b)
        (a == VNull && b == VUndefined) || (a == VUndefined && b == VNull) -> true
        a is VNumber && b is VString -> a.value == stringToNumber(b.value)
        a is VString && b is VNumber -> stringToNumber(a.value) == b.value
        a is VBool -> looseEq(VNumber(toNumber(a)), b)
        b is VBool -> looseEq(a, VNumber(toNumber(b)))
        (a is VNumber || a is VString) && isObjectLike(b) -> looseEq(a, toPrimitive(b))
        isObjectLike(a) && (b is VNumber || b is VString) -> looseEq(toPrimitive(a), b)
        else -> false
    }

    private fun isObjectLike(v: VValue): Boolean =
        v is VObject || v is VArray || v is VNamespace || v is VFunction || v is VMap || v is VSet

    private fun sameTypeCategory(a: VValue, b: VValue): Boolean = when (a) {
        is VNumber -> b is VNumber
        is VString -> b is VString
        is VBool -> b is VBool
        VNull -> b == VNull
        VUndefined -> b == VUndefined
        is VObject, is VArray, is VNamespace, is VFunction, is VMap, is VSet -> isObjectLike(b)
    }

    // Relational (<, <=, >, >=): both string -> lexicographic, else numeric (NaN -> false)
    private fun relational(a: VValue, b: VValue, cmp: (Int) -> Boolean, numCmp: (Double, Double) -> Boolean): Boolean {
        val pa = toPrimitive(a)
        val pb = toPrimitive(b)
        return if (pa is VString && pb is VString) {
            cmp(pa.value.compareTo(pb.value))
        } else {
            val na = toNumber(pa)
            val nb = toNumber(pb)
            if (na.isNaN() || nb.isNaN()) false else numCmp(na, nb)
        }
    }

    fun lt(a: VValue, b: VValue): Boolean = relational(a, b, { it < 0 }, { x, y -> x < y })
    fun le(a: VValue, b: VValue): Boolean = relational(a, b, { it <= 0 }, { x, y -> x <= y })
    fun gt(a: VValue, b: VValue): Boolean = relational(a, b, { it > 0 }, { x, y -> x > y })
    fun ge(a: VValue, b: VValue): Boolean = relational(a, b, { it >= 0 }, { x, y -> x >= y })

    // typeof
    fun typeOf(v: VValue): String = when (v) {
        is VNumber -> "number"
        is VBool -> "boolean"
        is VString -> "string"
        VUndefined -> "undefined"
        VNull -> "object"
        is VFunction -> "function"
        is VObject, is VArray, is VNamespace, is VMap, is VSet -> "object"
    }

    // `in` operator
    fun inOp(key: VValue, container: VValue): Boolean = when (container) {
        is VObject -> toStringJs(key) in container.fields
        is VArray -> {
            val idx = toNumber(key)
            idx == floor(idx) && idx >= 0 && idx < container.elements.size
        }

        else -> false
    }
}
