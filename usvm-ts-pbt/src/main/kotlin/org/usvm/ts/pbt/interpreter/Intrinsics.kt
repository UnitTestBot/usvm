package org.usvm.ts.pbt.interpreter

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Built-in functions/fields the concrete interpreter models.
 *
 * The registry covers exactly what the usvm-ts sample suite and its call
 * approximations use (`Number.isNaN`, `Math.floor`, array methods, `console`/`Logger`
 * no-ops, ...). A miss returns `null` and the caller reports [ExecutionResult.Unsupported] —
 * we never silently return a wrong value.
 */
internal object Intrinsics {

    val NAMESPACES: Set<String> = setOf("Math", "Number", "Boolean", "String", "console", "Logger", "JSON", "Object")

    /** Call on a namespace object: `Math.floor(x)`, `Number.isNaN(x)`, `console.log(...)`. */
    fun callNamespace(namespace: String, method: String, args: List<VValue>): VValue? = when (namespace) {
        "console", "Logger" -> VUndefined // logging is a no-op

        "Math" -> {
            val x = args.getOrElse(0) { VUndefined }
            when (method) {
                "floor" -> VNumber(floor(JsSemantics.toNumber(x)))
                "ceil" -> VNumber(ceil(JsSemantics.toNumber(x)))
                "round" -> VNumber(round(JsSemantics.toNumber(x)))
                "trunc" -> VNumber(kotlin.math.truncate(JsSemantics.toNumber(x)))
                "abs" -> VNumber(abs(JsSemantics.toNumber(x)))
                "sqrt" -> VNumber(sqrt(JsSemantics.toNumber(x)))
                "max" -> VNumber(args.map { JsSemantics.toNumber(it) }.maxOrNull() ?: Double.NEGATIVE_INFINITY)
                "min" -> VNumber(args.map { JsSemantics.toNumber(it) }.minOrNull() ?: Double.POSITIVE_INFINITY)
                "pow" -> VNumber(
                    Math.pow(
                        JsSemantics.toNumber(args.getOrElse(0) { VUndefined }),
                        JsSemantics.toNumber(args.getOrElse(1) { VUndefined }),
                    )
                )

                else -> null
            }
        }

        "Number" -> {
            val x = args.getOrElse(0) { VUndefined }
            when (method) {
                "isNaN" -> VBool(x is VNumber && x.value.isNaN())
                "isFinite" -> VBool(x is VNumber && x.value.isFinite())
                "isInteger" -> VBool(x is VNumber && x.value.isFinite() && x.value == floor(x.value))
                "parseFloat" -> VNumber(JsSemantics.stringToNumber(JsSemantics.toStringJs(x)))
                else -> null
            }
        }

        else -> null
    }

    /** Constant field on a namespace object: `Number.MAX_VALUE`, `Math.PI`. */
    fun namespaceField(namespace: String, field: String): VValue? = when (namespace) {
        "Number" -> when (field) {
            "MAX_VALUE" -> VNumber(Double.MAX_VALUE)
            "MIN_VALUE" -> VNumber(Double.MIN_VALUE)
            "MAX_SAFE_INTEGER" -> VNumber(9007199254740991.0)
            "MIN_SAFE_INTEGER" -> VNumber(-9007199254740991.0)
            "NaN" -> VNumber(Double.NaN)
            "POSITIVE_INFINITY" -> VNumber(Double.POSITIVE_INFINITY)
            "NEGATIVE_INFINITY" -> VNumber(Double.NEGATIVE_INFINITY)
            "EPSILON" -> VNumber(Math.ulp(1.0))
            else -> null
        }

        "Math" -> when (field) {
            "PI" -> VNumber(Math.PI)
            "E" -> VNumber(Math.E)
            else -> null
        }

        else -> null
    }

    /** Free-function-style conversions: `Number(x)`, `Boolean(x)`, `String(x)`. */
    fun callConversion(name: String, args: List<VValue>): VValue? {
        val x = args.getOrElse(0) { VUndefined }
        return when (name) {
            "Number" -> VNumber(if (args.isEmpty()) 0.0 else JsSemantics.toNumber(x))
            "Boolean" -> VBool(args.isNotEmpty() && JsSemantics.truthy(x))
            "String" -> VString(if (args.isEmpty()) "" else JsSemantics.toStringJs(x))
            "isNaN" -> VBool(JsSemantics.toNumber(x).isNaN())
            "parseFloat" -> VNumber(JsSemantics.stringToNumber(JsSemantics.toStringJs(x)))
            else -> null
        }
    }

    /** Instance method on a concrete receiver value: `arr.push(x)`, `n.toString()`. */
    fun callInstance(receiver: VValue, method: String, args: List<VValue>): VValue? {
        // Universal methods
        when (method) {
            "toString" -> return VString(JsSemantics.toStringJs(receiver))
            "valueOf" -> return JsSemantics.toPrimitive(receiver)
        }
        return when (receiver) {
            is VArray -> callArrayMethod(receiver, method, args)
            is VString -> callStringMethod(receiver, method, args)
            else -> null
        }
    }

    private fun callArrayMethod(arr: VArray, method: String, args: List<VValue>): VValue? = when (method) {
        "push" -> {
            arr.elements.addAll(args)
            VNumber(arr.elements.size.toDouble())
        }

        "pop" -> if (arr.elements.isEmpty()) VUndefined else arr.elements.removeAt(arr.elements.size - 1)

        "shift" -> if (arr.elements.isEmpty()) VUndefined else arr.elements.removeAt(0)

        "unshift" -> {
            arr.elements.addAll(0, args)
            VNumber(arr.elements.size.toDouble())
        }

        "fill" -> {
            val value = args.getOrElse(0) { VUndefined }
            for (i in arr.elements.indices) arr.elements[i] = value
            arr
        }

        "join" -> {
            val sep = args.getOrNull(0)?.let { JsSemantics.toStringJs(it) } ?: ","
            VString(arr.elements.joinToString(sep) { el ->
                if (el == VNull || el == VUndefined) "" else JsSemantics.toStringJs(el)
            })
        }

        "slice" -> {
            val size = arr.elements.size
            fun clamp(raw: Double, default: Int): Int {
                if (raw.isNaN()) return 0
                val i = raw.toInt()
                return when {
                    args.isEmpty() -> default
                    i < 0 -> maxOf(size + i, 0)
                    else -> minOf(i, size)
                }
            }

            val start = args.getOrNull(0)?.let { clamp(JsSemantics.toNumber(it), 0) } ?: 0
            val end = args.getOrNull(1)?.let { clamp(JsSemantics.toNumber(it), size) } ?: size
            VArray(if (start < end) arr.elements.subList(start, end).toMutableList() else mutableListOf())
        }

        "concat" -> {
            val result = arr.elements.toMutableList()
            for (a in args) {
                if (a is VArray) result.addAll(a.elements) else result.add(a)
            }
            VArray(result)
        }

        "indexOf" -> {
            val target = args.getOrElse(0) { VUndefined }
            VNumber(arr.elements.indexOfFirst { JsSemantics.strictEq(it, target) }.toDouble())
        }

        "includes" -> {
            val target = args.getOrElse(0) { VUndefined }
            // `includes` uses SameValueZero: NaN is found, unlike indexOf
            VBool(arr.elements.any { el ->
                JsSemantics.strictEq(el, target) ||
                    (el is VNumber && target is VNumber && el.value.isNaN() && target.value.isNaN())
            })
        }

        "reverse" -> {
            arr.elements.reverse()
            arr
        }

        else -> null
    }

    private fun callStringMethod(s: VString, method: String, args: List<VValue>): VValue? = when (method) {
        "charAt" -> {
            val i = JsSemantics.toNumber(args.getOrElse(0) { VNumber(0.0) }).toInt()
            VString(if (i in s.value.indices) s.value[i].toString() else "")
        }

        "indexOf" -> VNumber(
            s.value.indexOf(JsSemantics.toStringJs(args.getOrElse(0) { VUndefined })).toDouble()
        )

        "includes" -> VBool(s.value.contains(JsSemantics.toStringJs(args.getOrElse(0) { VUndefined })))

        "toUpperCase" -> VString(s.value.uppercase())
        "toLowerCase" -> VString(s.value.lowercase())
        "trim" -> VString(s.value.trim())

        "concat" -> VString(s.value + args.joinToString("") { JsSemantics.toStringJs(it) })

        "slice", "substring" -> {
            val len = s.value.length
            fun idx(v: VValue?, default: Int): Int {
                if (v == null) return default
                val d = JsSemantics.toNumber(v)
                if (d.isNaN()) return 0
                val i = d.toInt()
                return if (method == "slice" && i < 0) maxOf(len + i, 0) else i.coerceIn(0, len)
            }

            var start = idx(args.getOrNull(0), 0)
            var end = idx(args.getOrNull(1), len)
            if (method == "substring" && start > end) {
                val t = start; start = end; end = t
            }
            VString(if (start < end) s.value.substring(start, end) else "")
        }

        else -> null
    }
}
