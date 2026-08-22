package org.usvm.ts.pbt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stable canonical identifier used to correlate one property across artifacts and backends. */
@JvmInline
@Serializable
value class PropertyId private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        operator fun invoke(value: String): PropertyId {
            require(isCanonicalPropertyId(value)) { "Invalid property ID: $value" }
            return PropertyId(value)
        }

        internal fun unchecked(value: String): PropertyId = PropertyId(value)
    }
}

/**
 * Backend-independent Kotlin definition of one property.
 *
 * @property id stable identity of the property
 * @property inputs ordered domains matching positional TypeScript parameters
 * @property predicate TypeScript function that must hold for generated inputs
 * @property precondition optional TypeScript function that filters inputs before evaluation
 */
@Serializable
data class PropertyDefinition(
    val id: PropertyId,
    val inputs: List<PropertyInput>,
    val predicate: TypeScriptEntryPoint,
    val precondition: TypeScriptEntryPoint? = null,
)

/**
 * Names one positional property input and declares its backend-independent domain.
 *
 * @property name JavaScript identifier used in diagnostics and artifacts
 * @property domain values that concrete and symbolic backends may produce
 */
@Serializable
data class PropertyInput(
    val name: String,
    val domain: PropertyDomain,
)

/**
 * References an exported TypeScript function without loading or executing it.
 *
 * @property module normalized project-relative POSIX module path
 * @property exportName JavaScript identifier exported by [module]
 * @property executionKind whether invoking the function returns directly or asynchronously
 */
@Serializable
data class TypeScriptEntryPoint(
    val module: String,
    val exportName: String,
    val executionKind: ExecutionKind = ExecutionKind.SYNC,
)

/** Describes how a referenced TypeScript function completes. */
@Serializable
enum class ExecutionKind {
    /** The function returns its result directly. */
    @SerialName("sync")
    SYNC,

    /** The function returns an awaitable result. */
    @SerialName("async")
    ASYNC,
}

internal fun isCanonicalPropertyId(value: String): Boolean = PROPERTY_ID_REGEX.matches(value)

private val PROPERTY_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")
