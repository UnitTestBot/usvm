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

class VObject(
    /** Declaring class, if the object is an instance of a scene class; `null` for plain records. */
    val cls: EtsClass?,
    val fields: MutableMap<String, VValue> = mutableMapOf(),
) : VValue {
    override fun toString(): String = "VObject(${cls?.name ?: "<record>"}, $fields)"
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
) : VValue {
    override fun toString(): String = "VFunction(${method.name})"
}

/** A JS `Map` with SameValueZero keys (primitives by value, heap entities by identity). */
class VMap(
    val entries: LinkedHashMap<VValue, VValue> = LinkedHashMap(),
) : VValue {
    override fun toString(): String = "VMap($entries)"
}

/** A JS `Set` with SameValueZero elements. */
class VSet(
    val elements: LinkedHashSet<VValue> = LinkedHashSet(),
) : VValue {
    override fun toString(): String = "VSet($elements)"
}
