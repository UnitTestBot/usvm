package org.usvm.ts.pbt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val DEFAULT_MAX_STRING_LENGTH = 10
const val DEFAULT_MAX_ARRAY_LENGTH = 10

@Serializable
sealed interface PropertyDomain

@Serializable
@SerialName("boolean")
data object BooleanDomain : PropertyDomain

@Serializable
@SerialName("integer")
data class IntegerDomain(
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE,
) : PropertyDomain

@Serializable
@SerialName("number")
data class NumberDomain(
    val min: JsNumber = JsNumber.negativeInfinity(),
    val max: JsNumber = JsNumber.positiveInfinity(),
    val allowNaN: Boolean = true,
) : PropertyDomain

@Serializable
@SerialName("string")
data class StringDomain(
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_STRING_LENGTH,
) : PropertyDomain

@Serializable
@SerialName("constant")
data class ConstantDomain(val value: JsConcreteValue) : PropertyDomain

@Serializable
@SerialName("optional")
data class OptionalDomain(
    val value: PropertyDomain,
    val nil: JsConcreteValue = JsConcreteValue.Undefined,
) : PropertyDomain

@Serializable
@SerialName("tuple")
data class TupleDomain(val elements: List<PropertyDomain>) : PropertyDomain

@Serializable
@SerialName("array")
data class ArrayDomain(
    val element: PropertyDomain,
    val minLength: Int = 0,
    val maxLength: Int = DEFAULT_MAX_ARRAY_LENGTH,
) : PropertyDomain
