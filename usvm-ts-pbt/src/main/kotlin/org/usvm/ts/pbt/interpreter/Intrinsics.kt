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

    /** Host callback for invoking a first-class function value (HOF callbacks). */
    fun interface FunctionInvoker {
        fun invoke(fn: VFunction, args: List<VValue>): VValue
    }

    /** Call on a namespace object: `Math.floor(x)`, `Number.isNaN(x)`, `console.log(...)`. */
    fun callNamespace(namespace: String, method: String, args: List<VValue>): VValue? = when (namespace) {
        "console", "Logger" -> VUndefined // logging is a no-op

        "Math" -> {
            val x = args.getOrElse(0) { VUndefined }
            val n = JsSemantics.toNumber(x)
            when (method) {
                "floor" -> VNumber(floor(n))
                "ceil" -> VNumber(ceil(n))
                "round" -> VNumber(floor(n + 0.5)) // JS rounds .5 towards +Infinity
                "trunc" -> VNumber(kotlin.math.truncate(n))
                "abs" -> VNumber(abs(n))
                "sqrt" -> VNumber(sqrt(n))
                "cbrt" -> VNumber(kotlin.math.cbrt(n))
                "sign" -> VNumber(if (n.isNaN()) Double.NaN else kotlin.math.sign(n))
                "log" -> VNumber(kotlin.math.ln(n))
                "log2" -> VNumber(kotlin.math.log2(n))
                "log10" -> VNumber(kotlin.math.log10(n))
                "log1p" -> VNumber(kotlin.math.ln1p(n))
                "exp" -> VNumber(kotlin.math.exp(n))
                "expm1" -> VNumber(kotlin.math.expm1(n))
                "sin" -> VNumber(kotlin.math.sin(n))
                "cos" -> VNumber(kotlin.math.cos(n))
                "tan" -> VNumber(kotlin.math.tan(n))
                "asin" -> VNumber(kotlin.math.asin(n))
                "acos" -> VNumber(kotlin.math.acos(n))
                "atan" -> VNumber(kotlin.math.atan(n))
                "sinh" -> VNumber(kotlin.math.sinh(n))
                "cosh" -> VNumber(kotlin.math.cosh(n))
                "tanh" -> VNumber(kotlin.math.tanh(n))
                "max" -> VNumber(args.map { JsSemantics.toNumber(it) }.maxOrNull() ?: Double.NEGATIVE_INFINITY)
                "min" -> VNumber(args.map { JsSemantics.toNumber(it) }.minOrNull() ?: Double.POSITIVE_INFINITY)
                "hypot" -> VNumber(sqrt(args.sumOf { val v = JsSemantics.toNumber(it); v * v }))
                "atan2" -> VNumber(
                    kotlin.math.atan2(n, JsSemantics.toNumber(args.getOrElse(1) { VUndefined }))
                )

                "pow" -> VNumber(
                    Math.pow(n, JsSemantics.toNumber(args.getOrElse(1) { VUndefined }))
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
                "isSafeInteger" -> VBool(
                    x is VNumber && x.value.isFinite() && x.value == floor(x.value) &&
                        abs(x.value) <= 9007199254740991.0
                )

                "parseFloat" -> VNumber(JsSemantics.stringToNumber(JsSemantics.toStringJs(x)))
                "parseInt" -> parseIntJs(args)
                else -> null
            }
        }

        "Object" -> when (method) {
            "keys" -> when (val o = args.getOrElse(0) { VUndefined }) {
                is VObject -> VArray(o.fields.keys.map { VString(it) as VValue }.toMutableList())
                is VArray -> VArray(o.elements.indices.map { VString(it.toString()) as VValue }.toMutableList())
                else -> VArray()
            }

            "values" -> when (val o = args.getOrElse(0) { VUndefined }) {
                is VObject -> VArray(o.fields.values.toMutableList())
                is VArray -> VArray(o.elements.toMutableList())
                else -> VArray()
            }

            else -> null
        }

        else -> null
    }

    private fun parseIntJs(args: List<VValue>): VNumber {
        val s = JsSemantics.toStringJs(args.getOrElse(0) { VUndefined }).trim()
        val radix = args.getOrNull(1)?.let { JsSemantics.toInt32(it) }?.takeIf { it != 0 } ?: 10
        var i = 0
        var sign = 1
        if (i < s.length && (s[i] == '+' || s[i] == '-')) {
            if (s[i] == '-') sign = -1
            i++
        }
        var body = s.substring(i)
        var r = radix
        if ((r == 16 || args.getOrNull(1) == null) && (body.startsWith("0x") || body.startsWith("0X"))) {
            body = body.substring(2)
            r = 16
        }
        val digits = body.takeWhile { Character.digit(it, r) >= 0 }
        if (digits.isEmpty()) return VNumber(Double.NaN)
        var acc = 0.0
        for (c in digits) acc = acc * r + Character.digit(c, r)
        return VNumber(sign * acc)
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
    fun callInstance(
        receiver: VValue,
        method: String,
        args: List<VValue>,
        invoker: FunctionInvoker,
    ): VValue? {
        // Universal methods
        when (method) {
            "toString" -> return VString(JsSemantics.toStringJs(receiver))
            "valueOf" -> return JsSemantics.toPrimitive(receiver)
        }
        return when (receiver) {
            is VArray -> callArrayMethod(receiver, method, args)
                ?: callArrayHof(receiver, method, args, invoker)

            is VString -> callStringMethod(receiver, method, args)
            is VMap -> callMapMethod(receiver, method, args, invoker)
            is VSet -> callSetMethod(receiver, method, args, invoker)
            else -> null
        }
    }

    private fun callArrayHof(
        arr: VArray,
        method: String,
        args: List<VValue>,
        invoker: FunctionInvoker,
    ): VValue? {
        val fn = args.getOrNull(0) as? VFunction
        fun call(vararg a: VValue): VValue = invoker.invoke(fn!!, a.toList())

        return when (method) {
            "map" -> {
                fn ?: return null
                VArray(arr.elements.mapIndexed { i, e -> call(e, VNumber(i.toDouble()), arr) }.toMutableList())
            }

            "filter" -> {
                fn ?: return null
                VArray(
                    arr.elements.filterIndexed { i, e ->
                        JsSemantics.truthy(call(e, VNumber(i.toDouble()), arr))
                    }.toMutableList()
                )
            }

            "forEach" -> {
                fn ?: return null
                arr.elements.forEachIndexed { i, e -> call(e, VNumber(i.toDouble()), arr) }
                VUndefined
            }

            "some" -> {
                fn ?: return null
                VBool(arr.elements.withIndex().any { (i, e) -> JsSemantics.truthy(call(e, VNumber(i.toDouble()), arr)) })
            }

            "every" -> {
                fn ?: return null
                VBool(arr.elements.withIndex().all { (i, e) -> JsSemantics.truthy(call(e, VNumber(i.toDouble()), arr)) })
            }

            "find" -> {
                fn ?: return null
                arr.elements.withIndex()
                    .firstOrNull { (i, e) -> JsSemantics.truthy(call(e, VNumber(i.toDouble()), arr)) }
                    ?.value ?: VUndefined
            }

            "findIndex" -> {
                fn ?: return null
                VNumber(
                    arr.elements.withIndex()
                        .indexOfFirst { (i, e) -> JsSemantics.truthy(call(e, VNumber(i.toDouble()), arr)) }
                        .toDouble()
                )
            }

            "reduce" -> {
                fn ?: return null
                var acc: VValue
                var start: Int
                if (args.size >= 2) {
                    acc = args[1]
                    start = 0
                } else {
                    if (arr.elements.isEmpty()) {
                        throw JsThrowSignal(VString("TypeError: Reduce of empty array with no initial value"))
                    }
                    acc = arr.elements[0]
                    start = 1
                }
                for (i in start until arr.elements.size) {
                    acc = call(acc, arr.elements[i], VNumber(i.toDouble()), arr)
                }
                acc
            }

            "sort" -> {
                val comparator: Comparator<VValue> = if (fn != null) {
                    Comparator { a, b ->
                        val r = JsSemantics.toNumber(call(a, b))
                        if (r.isNaN()) 0 else r.compareTo(0.0)
                    }
                } else {
                    // Default JS sort: by ToString, undefined last
                    Comparator { a, b -> JsSemantics.toStringJs(a).compareTo(JsSemantics.toStringJs(b)) }
                }
                arr.elements.sortWith(comparator)
                arr
            }

            else -> null
        }
    }

    private fun callMapMethod(
        map: VMap,
        method: String,
        args: List<VValue>,
        invoker: FunctionInvoker,
    ): VValue? {
        fun key(v: VValue): VValue = if (v is VNumber && v.value == 0.0) VNumber(0.0) else v // normalize -0
        val k = key(args.getOrElse(0) { VUndefined })
        return when (method) {
            "get" -> map.entries[k] ?: VUndefined
            "set" -> {
                map.entries[k] = args.getOrElse(1) { VUndefined }
                map
            }

            "has" -> VBool(k in map.entries)
            "delete" -> VBool(map.entries.remove(k) != null)
            "clear" -> {
                map.entries.clear()
                VUndefined
            }

            "keys" -> VArray(map.entries.keys.toMutableList())
            "values" -> VArray(map.entries.values.toMutableList())
            "forEach" -> {
                val fn = args.getOrNull(0) as? VFunction ?: return null
                for ((kk, v) in map.entries.entries.toList()) {
                    invoker.invoke(fn, listOf(v, kk, map))
                }
                VUndefined
            }

            else -> null
        }
    }

    private fun callSetMethod(
        set: VSet,
        method: String,
        args: List<VValue>,
        invoker: FunctionInvoker,
    ): VValue? {
        fun key(v: VValue): VValue = if (v is VNumber && v.value == 0.0) VNumber(0.0) else v
        val k = key(args.getOrElse(0) { VUndefined })
        return when (method) {
            "add" -> {
                set.elements.add(k)
                set
            }

            "has" -> VBool(k in set.elements)
            "delete" -> VBool(set.elements.remove(k))
            "clear" -> {
                set.elements.clear()
                VUndefined
            }

            "values", "keys" -> VArray(set.elements.toMutableList())
            "forEach" -> {
                val fn = args.getOrNull(0) as? VFunction ?: return null
                for (e in set.elements.toList()) {
                    invoker.invoke(fn, listOf(e, e, set))
                }
                VUndefined
            }

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
