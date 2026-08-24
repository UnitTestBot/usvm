package org.usvm.ts.pbt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val DEFAULT_MAX_STRING_LENGTH = 10
const val DEFAULT_MAX_ARRAY_LENGTH = 10

/** Backend-independent set of JavaScript values that a property input may receive. */
@Serializable
sealed interface PropertyDomain

/** Domain containing both JavaScript boolean values. */
@Serializable
@SerialName("boolean")
data object BooleanDomain : PropertyDomain

/** Inclusive domain of signed 32-bit integers. */
@Serializable
@SerialName("integer")
data class IntegerDomain(
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE,
) : PropertyDomain

/**
 * Inclusive ECMAScript binary64 domain.
 *
 * Tagged infinities are valid bounds; [allowNaN] controls whether NaN belongs to an otherwise unbounded domain.
 */
@Serializable
@SerialName("number")
data class NumberDomain(
    val min: JsNumber = JsNumber.negativeInfinity(),
    val max: JsNumber = JsNumber.positiveInfinity(),
    val allowNaN: Boolean = true,
) : PropertyDomain

/** Domain of arbitrary UTF-16 code-unit sequences within the inclusive length bounds. */
@Serializable
@SerialName("string")
data class StringDomain(
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_STRING_LENGTH,
) : PropertyDomain

/** Singleton domain containing one tagged JavaScript primitive. */
@Serializable
@SerialName("constant")
data class ConstantDomain(val value: JsConcreteValue) : PropertyDomain

/** Domain containing [value] plus exactly one nullish [nil] value. */
@Serializable
@SerialName("optional")
data class OptionalDomain(
    val value: PropertyDomain,
    val nil: JsConcreteValue = JsConcreteValue.Undefined,
) : PropertyDomain

/** Fixed-length ordered product of non-empty recursive domains. */
@Serializable
@SerialName("tuple")
data class TupleDomain(val elements: List<PropertyDomain>) : PropertyDomain

/** Recursive array domain with inclusive JavaScript length bounds. */
@Serializable
@SerialName("array")
data class ArrayDomain(
    val element: PropertyDomain,
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_ARRAY_LENGTH,
) : PropertyDomain

/** Returns whether [value] belongs to this domain, including recursive tuple and array constraints. */
operator fun PropertyDomain.contains(value: JsConcreteValue): Boolean = when (this) {
    BooleanDomain -> value is JsConcreteValue.Boolean
    is IntegerDomain -> value is JsConcreteValue.Number && value.isIntegerIn(this)
    is NumberDomain -> value is JsConcreteValue.Number && value.isNumberIn(this)
    is StringDomain -> value is JsConcreteValue.String && value.value.length in minLength..maxLength
    is ConstantDomain -> value == this.value
    is OptionalDomain -> value == nil || value in this.value
    is TupleDomain ->
        value is JsConcreteValue.Array &&
            value.elements.size == elements.size &&
            value.elements.zip(elements).all { (element, domain) -> element in domain }

    is ArrayDomain ->
        value is JsConcreteValue.Array &&
            value.elements.size in minLength..maxLength &&
            value.elements.all { elementValue -> elementValue in element }
}

private fun JsConcreteValue.Number.isIntegerIn(domain: IntegerDomain): Boolean {
    if (number.bits == NEGATIVE_ZERO_BITS) return false

    val value = validDoubleOrNull() ?: return false
    return value.isFinite() && value % 1.0 == 0.0 && value in domain.min.toDouble()..domain.max.toDouble()
}

private fun JsConcreteValue.Number.isNumberIn(domain: NumberDomain): Boolean {
    if (number.value == JsNumberKind.NAN) return domain.allowNaN

    val value = validDoubleOrNull() ?: return false
    val minimum = runCatching(domain.min::toDouble).getOrNull() ?: return false
    val maximum = runCatching(domain.max::toDouble).getOrNull() ?: return false

    return value in minimum..maximum
}

private fun JsConcreteValue.Number.validDoubleOrNull(): Double? = runCatching(::toDouble).getOrNull()

private const val NEGATIVE_ZERO_BITS = "8000000000000000"
