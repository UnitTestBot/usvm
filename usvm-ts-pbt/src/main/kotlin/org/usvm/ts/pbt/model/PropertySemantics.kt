package org.usvm.ts.pbt.model

import kotlinx.serialization.Serializable

/** Stable author-assigned identity and exact source point of an original assertion. */
@Serializable
data class PropertyAssertion(
    val id: String,
    val source: PropertySourcePoint,
    val testedCall: PropertySourcePoint? = null,
    val operands: List<PropertyOperand> = emptyList(),
)

/** Stable identity of an explicitly named assertion operand, without evaluating it. */
@Serializable
data class PropertyOperand(
    val id: String,
    val source: PropertySourcePoint,
)

/** One-based source position in a project-relative TypeScript module. */
@Serializable
data class PropertySourcePoint(
    val module: String,
    val line: Int,
    val column: Int,
)

/** Hashes supplied by the registrant; scope is the named source file and recorded build artifact. */
@Serializable
data class PropertySourceIdentity(
    val sourceSha256: String,
    val buildSha256: String,
    val buildScope: String,
)

/** A supported joint construction relation, independent of sampling probability or size bias. */
@Serializable
data class ArrayIndexGenerator(
    val id: String,
    val arrayInputIndex: Int,
    val indexInputIndex: Int,
    val kind: String = "array-index",
)

/** Checks only declared support; preconditions remain original TypeScript code. */
fun ArrayIndexGenerator.accepts(values: List<JsConcreteValue>): Boolean {
    val array = values.getOrNull(arrayInputIndex) as? JsConcreteValue.Array ?: return false
    val index = values.getOrNull(indexInputIndex) as? JsConcreteValue.Number ?: return false
    val number = runCatching(index::toDouble).getOrNull() ?: return false

    return number.isFinite() && number % 1.0 == 0.0 && number >= 0 && number < array.elements.size
}
