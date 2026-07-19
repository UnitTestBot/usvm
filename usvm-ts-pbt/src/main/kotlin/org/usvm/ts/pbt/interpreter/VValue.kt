package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsMethod

/**
 * A concrete runtime value of the EtsIR interpreter, mirroring the JS value universe
 * supported by usvm-ts (`org.usvm.api.TsTestValue`).
 *
 * [VObject] and [VArray] intentionally use *identity* equality (JS reference semantics).
 */
sealed interface VValue

data class VNumber(val value: Double) : VValue {
    override fun toString(): String = "VNumber($value)"
}

data class VBool(val value: Boolean) : VValue

data class VString(val value: String) : VValue

data object VNull : VValue

data object VUndefined : VValue

open class VObject(
    /** Declaring class, if the object is an instance of a scene class; `null` for plain records. */
    val cls: EtsClass?,
    val fields: MutableMap<String, VValue> = mutableMapOf(),
    /** Explicit prototype for ETC/plain objects. Scene instances use [cls]'s superclass chain. */
    val prototype: VObject? = null,
) : VValue {
    override fun toString(): String = "VObject(${cls?.name ?: "<record>"}, $fields)"
}

/** Execution-local host callable used for exact built-in protocol functions. */
class VNativeFunction internal constructor() : VObject(cls = null) {
    override fun toString(): String = "VNativeFunction"
}

class VArray(
    val elements: MutableList<VValue> = mutableListOf(),
) : VValue {
    override fun toString(): String = "VArray($elements)"
}

/**
 * An intrinsic namespace object (`Math`, `console`, `Number`, `Logger`, ...).
 * Appears as the receiver of intrinsic calls; behaves as a plain object otherwise.
 */
data class VNamespace(val name: String) : VValue

/**
 * A first-class function value: an arrow function / function expression lowered
 * by the front end into an anonymous method. Passed around as callbacks
 * (`arr.map(f)`) and invoked via `ptr_call`.
 */
class VFunction(
    val method: EtsMethod,
    /** Captured `this`, when the function was obtained from an instance context. */
    val thisValue: VValue = VUndefined,
    /** Whether call-site receiver binding is dynamic, lexical (arrow), or permanently bound. */
    val thisMode: VFunctionThisMode = VFunctionThisMode.DYNAMIC,
) : VValue {
    override fun toString(): String = "VFunction(${method.name})"
}

enum class VFunctionThisMode {
    DYNAMIC,
    LEXICAL,
    BOUND,
}

/** A JS `Map` with SameValueZero keys (primitives by value, heap entities by identity). */
class VMap(
    entries: LinkedHashMap<VValue, VValue> = LinkedHashMap(),
) : VValue {
    val entries: LinkedHashMap<VValue, VValue> = SameValueZeroMap(entries)

    override fun toString(): String = "VMap($entries)"
}

/** A JS `Set` with SameValueZero elements. */
class VSet(
    elements: LinkedHashSet<VValue> = LinkedHashSet(),
) : VValue {
    val elements: LinkedHashSet<VValue> = SameValueZeroSet(elements)

    override fun toString(): String = "VSet($elements)"
}

internal fun sameValueZeroKey(value: VValue): VValue =
    if (value is VNumber && value.value == 0.0) VNumber(0.0) else value

private class SameValueZeroMap(initial: Map<VValue, VValue>) : LinkedHashMap<VValue, VValue>() {
    init {
        putAll(initial)
    }

    override fun put(key: VValue, value: VValue): VValue? = super.put(sameValueZeroKey(key), value)

    override fun putAll(from: Map<out VValue, VValue>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    override fun get(key: VValue): VValue? = super.get(sameValueZeroKey(key))

    override fun containsKey(key: VValue): Boolean = super.containsKey(sameValueZeroKey(key))

    override fun remove(key: VValue): VValue? = super.remove(sameValueZeroKey(key))
}

private class SameValueZeroSet(initial: Collection<VValue>) : LinkedHashSet<VValue>() {
    init {
        addAll(initial)
    }

    override fun add(element: VValue): Boolean = super.add(sameValueZeroKey(element))

    override fun addAll(elements: Collection<VValue>): Boolean =
        elements.fold(false) { changed, element -> add(element) || changed }

    override fun contains(element: VValue): Boolean = super.contains(sameValueZeroKey(element))

    override fun remove(element: VValue): Boolean = super.remove(sameValueZeroKey(element))
}
