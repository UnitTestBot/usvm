package org.usvm.ts.pbt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class PropertyDefinition(
    val id: PropertyId,
    val inputs: List<PropertyInput>,
    val predicate: TypeScriptEntryPoint,
    val precondition: TypeScriptEntryPoint? = null,
)

@Serializable
data class PropertyInput(
    val name: String,
    val domain: PropertyDomain,
)

@Serializable
data class TypeScriptEntryPoint(
    val module: String,
    val exportName: String,
    val executionKind: ExecutionKind = ExecutionKind.SYNC,
)

@Serializable
enum class ExecutionKind {
    @SerialName("sync")
    SYNC,

    @SerialName("async")
    ASYNC,
}

internal fun isCanonicalPropertyId(value: String): Boolean = PROPERTY_ID_REGEX.matches(value)

private val PROPERTY_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")
