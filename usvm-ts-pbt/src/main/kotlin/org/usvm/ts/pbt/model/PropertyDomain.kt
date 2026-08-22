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
