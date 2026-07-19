package org.usvm.ts.pbt.interpreter

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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

    val NAMESPACES: Set<String> = setOf(
        "Array", "Math", "Number", "Boolean", "String", "console", "Logger", "JSON", "Object", "Symbol",
    )

    /** Host callback for invoking a first-class function value (HOF callbacks). */
    fun interface FunctionInvoker {
        fun invoke(fn: VFunction, args: List<VValue>): VValue
    }

    /** Call on a namespace object: `Math.floor(x)`, `Number.isNaN(x)`, `console.log(...)`. */
    fun callNamespace(namespace: String, method: String, args: List<VValue>): VValue? = when (namespace) {
        "console", "Logger" -> VUndefined // logging is a no-op

        "JSON" -> when (method) {
            "stringify" -> VString(jsonStringify(args.getOrElse(0) { VUndefined }, mutableSetOf()) ?: "undefined")
            else -> null
        }

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
                "hypot" -> VNumber(
                    sqrt(
                        args.sumOf {
                            val value = JsSemantics.toNumber(it)
                            value * value
                        },
                    ),
                )
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

        "Array" -> when (method) {
            "isArray" -> VBool(args.getOrElse(0) { VUndefined } is VArray)
            else -> null
        }

        "Object" -> when (method) {
            "hasOwn" -> VBool(hasOwn(args.getOrElse(0) { VUndefined }, args.getOrElse(1) { VUndefined }))
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

    /**
     * `JSON.stringify` per ES semantics for the modeled value universe:
     * undefined/functions are omitted in objects and become null in arrays;
     * Map/Set serialize as plain empty objects; circular structures throw.
     * @return null when the top-level value is not serializable (undefined/function).
     */
    private fun jsonStringify(v: VValue, visited: MutableSet<VValue>): String? = when (v) {
        VUndefined, is VFunction, is VNativeFunction, is VNamespace -> null
        VNull -> "null"
        is VBool -> v.value.toString()
        is VNumber -> if (v.value.isFinite()) JsSemantics.numberToString(v.value) else "null"
        is VString -> jsonQuote(v.value)
        is VMap, is VSet -> "{}"
        is VArray -> {
            if (!visited.add(v)) throw JsThrowSignal(VString("TypeError: Converting circular structure to JSON"))
            val body = v.elements.joinToString(",") { jsonStringify(it, visited) ?: "null" }
            visited.remove(v)
            "[$body]"
        }

        is VObject -> {
            if (!visited.add(v)) throw JsThrowSignal(VString("TypeError: Converting circular structure to JSON"))
            val body = v.fields.entries.mapNotNull { (k, value) ->
                jsonStringify(value, visited)?.let { "${jsonQuote(k)}:$it" }
            }.joinToString(",")
            visited.remove(v)
            "{$body}"
        }
    }

    private fun jsonQuote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
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

        "Object" -> when (field) {
            "prototype" -> VNamespace("Object.prototype")
            else -> null
        }

        "Symbol" -> when (field) {
            "iterator" -> VNamespace("Symbol.iterator")
            else -> null
        }

        "Object.prototype" -> when (field) {
            "toString", "hasOwnProperty" -> VNamespace("Object.prototype.$field")
            else -> null
        }

        else -> null
    }

    /** Exact result subset for `Object.prototype.<method>.call(receiver, ...)`. */
    fun callObjectPrototype(method: String, receiver: VValue, args: List<VValue>): VValue? = when (method) {
        "toString" -> VString(objectTag(receiver))
        "hasOwnProperty" -> VBool(hasOwn(receiver, args.getOrElse(0) { VUndefined }))
        else -> null
    }

    private fun objectTag(value: VValue): String = when (value) {
        VUndefined -> "[object Undefined]"
        VNull -> "[object Null]"
        is VBool -> "[object Boolean]"
        is VNumber -> "[object Number]"
        is VString -> "[object String]"
        is VArray -> "[object Array]"
        is VFunction, is VNativeFunction -> "[object Function]"
        is VMap -> "[object Map]"
        is VSet -> "[object Set]"
        is VNamespace, is VObject -> "[object Object]"
    }

    private fun hasOwn(receiver: VValue, key: VValue): Boolean {
        if (receiver == VNull || receiver == VUndefined) {
            throw typeError("cannot convert ${JsSemantics.toStringJs(receiver)} to object")
        }
        val property = JsSemantics.toStringJs(key)
        return when (receiver) {
            is VObject -> receiver.fields.containsKey(property)
            is VArray -> property == "length" || property.toIntOrNull()?.let { it in receiver.elements.indices } == true
            is VString -> property == "length" || property.toIntOrNull()?.let { it in receiver.value.indices } == true
            is VMap, is VSet -> false
            else -> false
        }
    }

    /** Free-function-style conversions: `Number(x)`, `Boolean(x)`, `String(x)`. */
    fun callConversion(name: String, args: List<VValue>): VValue? {
        val x = args.getOrElse(0) { VUndefined }
        return when (name) {
            "Number" -> VNumber(if (args.isEmpty()) 0.0 else JsSemantics.toNumber(x))
            "Boolean" -> VBool(args.isNotEmpty() && JsSemantics.truthy(x))
            "String" -> VString(if (args.isEmpty()) "" else JsSemantics.toStringJs(x))
            "isNaN" -> VBool(JsSemantics.toNumber(x).isNaN())
            "isFinite" -> VBool(JsSemantics.toNumber(x).isFinite())
            "parseInt" -> parseIntJs(args)
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
                VBool(
                    arr.elements.withIndex().any { (index, element) ->
                        JsSemantics.truthy(call(element, VNumber(index.toDouble()), arr))
                    },
                )
            }

            "every" -> {
                fn ?: return null
                VBool(
                    arr.elements.withIndex().all { (index, element) ->
                        JsSemantics.truthy(call(element, VNumber(index.toDouble()), arr))
                    },
                )
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
        val k = sameValueZeroKey(args.getOrElse(0) { VUndefined })
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
        val k = sameValueZeroKey(args.getOrElse(0) { VUndefined })
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
            VString(
                arr.elements.joinToString(sep) { el ->
                    if (el == VNull || el == VUndefined) "" else JsSemantics.toStringJs(el)
                },
            )
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
            VBool(
                arr.elements.any { el ->
                    JsSemantics.strictEq(el, target) ||
                        (el is VNumber && target is VNumber && el.value.isNaN() && target.value.isNaN())
                },
            )
        }

        "reverse" -> {
            arr.elements.reverse()
            arr
        }

        "splice" -> {
            val size = arr.elements.size
            val relativeStart = integerOrInfinity(args.getOrElse(0) { VNumber(0.0) })
            val start = when {
                relativeStart == Double.NEGATIVE_INFINITY -> 0
                relativeStart < 0 -> (size + relativeStart).coerceAtLeast(0.0).toInt()
                relativeStart >= size -> size
                else -> relativeStart.toInt()
            }
            val deleteCount = when {
                args.isEmpty() -> 0
                args.size == 1 -> size - start
                else -> integerOrInfinity(args[1]).coerceIn(0.0, (size - start).toDouble()).toInt()
            }
            val removed = MutableList(deleteCount) { arr.elements.removeAt(start) }
            if (args.size > 2) arr.elements.addAll(start, args.drop(2))
            VArray(removed)
        }

        else -> null
    }

    private fun integerOrInfinity(value: VValue): Double {
        val number = JsSemantics.toNumber(value)
        return if (number.isNaN() || number == 0.0) 0.0 else kotlin.math.truncate(number)
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

        "split" -> {
            val sep = args.getOrNull(0)
            when {
                sep == null || sep == VUndefined -> VArray(mutableListOf(s))
                else -> {
                    val sepStr = JsSemantics.toStringJs(sep)
                    val parts = if (sepStr.isEmpty()) {
                        s.value.map { it.toString() }
                    } else {
                        s.value.split(sepStr)
                    }
                    VArray(parts.map { VString(it) as VValue }.toMutableList())
                }
            }
        }

        "localeCompare" -> VNumber(
            s.value.compareTo(JsSemantics.toStringJs(args.getOrElse(0) { VUndefined }))
                .coerceIn(-1, 1).toDouble()
        )

        "startsWith" -> VBool(s.value.startsWith(JsSemantics.toStringJs(args.getOrElse(0) { VUndefined })))
        "endsWith" -> VBool(s.value.endsWith(JsSemantics.toStringJs(args.getOrElse(0) { VUndefined })))

        "charCodeAt" -> {
            val i = JsSemantics.toNumber(args.getOrElse(0) { VNumber(0.0) }).toInt()
            if (i in s.value.indices) VNumber(s.value[i].code.toDouble()) else VNumber(Double.NaN)
        }

        "repeat" -> {
            val n = JsSemantics.toNumber(args.getOrElse(0) { VUndefined })
            if (n.isNaN() || n < 0) throw JsThrowSignal(VString("RangeError: Invalid count value"))
            VString(s.value.repeat(n.toInt()))
        }

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
                val temporary = start
                start = end
                end = temporary
            }
            VString(if (start < end) s.value.substring(start, end) else "")
        }

        else -> null
    }
}
