package org.usvm.ts.pbt.inference

import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.observation.ObservationPointKind

/** Only adapter-owned entry points can turn declared input support into a fact about an observed value. */
internal fun ScalarRelation.isDomainImplied(property: PropertyDefinition): Boolean {
    if (status != ScalarRelationStatus.SAMPLED || evidence.isEmpty()) return false

    val points = listOf(pair.left, pair.right)
    val entryArguments = points.all { point -> point.kind == ObservationPointKind.ENTRY_ARGUMENT }
    if (!entryArguments) return false

    val leftIndex = pair.left.inputIndex ?: return false
    val rightIndex = pair.right.inputIndex ?: return false
    val leftDomain = property.inputs.getOrNull(leftIndex)?.domain ?: return false
    val rightDomain = property.inputs.getOrNull(rightIndex)?.domain ?: return false
    val leftBounds = leftDomain.numericBounds() ?: return false
    val rightBounds = rightDomain.numericBounds() ?: return false
    val snapshotsMatch = evidence.all { sample ->
        val input = sample.invocation.input.value as? JsConcreteValue.Array
        val left = sample.left.value.value
        val right = sample.right.value.value

        val identitiesMatch = sample.left.matches(pair.left) && sample.right.matches(pair.right)
        val originalInputsMatch = left == input?.elements?.getOrNull(leftIndex) &&
            right == input?.elements?.getOrNull(rightIndex)
        val leftInDomain = left != null && left in leftDomain
        val rightInDomain = right != null && right in rightDomain

        identitiesMatch && originalInputsMatch && leftInDomain && rightInDomain
    }
    if (!snapshotsMatch) return false

    if (leftIndex == rightIndex) return true

    return when (template) {
        ScalarRelationTemplate.STRICT_EQUAL -> {
            leftBounds.minimum == leftBounds.maximum && rightBounds.minimum == rightBounds.maximum &&
                leftBounds.minimum == rightBounds.minimum
        }

        ScalarRelationTemplate.LESS_OR_EQUAL -> leftBounds.maximum <= rightBounds.minimum
    }
}

private data class NumericBounds(val minimum: Double, val maximum: Double)

/** Bounds are useful only when every allowed value is a number and NaN is excluded. */
private fun PropertyDomain.numericBounds(): NumericBounds? = when (this) {
    is IntegerDomain -> {
        val minimum = min.toDouble()
        val maximum = max.toDouble()

        if (min <= max) NumericBounds(minimum, maximum) else null
    }

    is NumberDomain -> {
        val validEncoding = min.hasValidEncoding() && max.hasValidEncoding()
        if (allowNaN || !validEncoding) {
            null
        } else {
            val minimum = min.toDouble()
            val maximum = max.toDouble()
            val ordered = !minimum.isNaN() && !maximum.isNaN() && minimum <= maximum

            if (ordered) NumericBounds(minimum, maximum) else null
        }
    }

    is ConstantDomain -> {
        val number = value as? JsConcreteValue.Number
        val validEncoding = number?.number?.hasValidEncoding() == true
        if (!validEncoding) {
            null
        } else {
            val scalar = requireNotNull(number).toDouble()

            if (scalar.isNaN()) null else NumericBounds(scalar, scalar)
        }
    }

    else -> null
}
