package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.SourcePosition
import org.usvm.ts.pbt.backend.SourceRange

/** A JSON value paired with its Istanbul path for precise validation diagnostics. */
internal class IstanbulJsonValue(
    private val value: JsonElement,
    val path: String,
) {
    fun asObject(): JsonObject = value as? JsonObject
        ?: throw invalidReport(
            message = "Expected an object",
            path = path,
        )

    fun asArray(): JsonArray = value as? JsonArray
        ?: throw invalidReport(
            message = "Expected an array",
            path = path,
        )

    fun required(name: String): IstanbulJsonValue {
        val childPath = "$path.$name"
        val child = asObject()[name]
            ?: throw invalidReport(
                message = "Missing required field $name",
                path = childPath,
            )

        return IstanbulJsonValue(child, childPath)
    }

    fun requiredObject(name: String): JsonObject = required(name).asObject()

    fun requiredString(name: String): String {
        val childPath = "$path.$name"
        val primitive = asObject()[name] as? JsonPrimitive
            ?: throw invalidReport(
                message = "Expected a string",
                path = childPath,
            )
        val content = primitive.contentOrNull

        if (!primitive.isString || content == null) {
            throw invalidReport(
                message = "Expected a string",
                path = childPath,
            )
        }

        return content
    }

    fun optionalString(name: String): String? {
        val child = asObject()[name] ?: return null
        val childPath = "$path.$name"
        val primitive = child as? JsonPrimitive
            ?: throw invalidReport(
                message = "Expected a string",
                path = childPath,
            )
        val content = primitive.contentOrNull ?: return null

        if (!primitive.isString) {
            throw invalidReport(
                message = "Expected a string",
                path = childPath,
            )
        }

        return content
    }

    fun asRange(): SourceRange {
        val start = required(name = "start").asPosition()
        val end = required(name = "end").asPosition()

        return SourceRange(start = start, end = end)
    }

    fun asPosition(): SourcePosition {
        val line = requiredInt(name = "line")
        val column = requiredInt(name = "column")

        return SourcePosition(line = line, column = column)
    }

    fun asHitCount(): Long {
        val primitive = value as? JsonPrimitive
            ?: throw invalidReport(
                message = "Expected an integer",
                path = path,
            )

        return primitive.longOrNull
            ?: throw invalidReport(
                message = "Expected an integer",
                path = path,
            )
    }

    private fun requiredInt(name: String): Int {
        val childPath = "$path.$name"
        val primitive = asObject()[name] as? JsonPrimitive
            ?: throw invalidReport(
                message = "Expected an integer",
                path = childPath,
            )

        return primitive.intOrNull
            ?: throw invalidReport(
                message = "Expected an integer",
                path = childPath,
            )
    }

    internal companion object {
        fun invalidReport(message: String, path: String): CoverageArtifactException =
            CoverageArtifactException.create(
                code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
                message = message,
                path = path,
            )
    }
}
