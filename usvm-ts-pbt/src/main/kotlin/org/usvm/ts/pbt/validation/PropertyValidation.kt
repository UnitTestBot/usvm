package org.usvm.ts.pbt.validation

import org.usvm.ts.pbt.manifest.PROPERTY_MANIFEST_SCHEMA_VERSION
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.JsNumberKind
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.isCanonicalPropertyId

data class ValidationDiagnostic(
    val code: String,
    val message: String,
    val path: String,
)

data class PropertyValidationResult(val diagnostics: List<ValidationDiagnostic>) {
    val isValid: Boolean
        get() = diagnostics.isEmpty()
}

class InvalidPropertyDefinitionException(
    val result: PropertyValidationResult,
) : IllegalArgumentException(result.diagnostics.joinToString(separator = "; ") { "${it.path}: ${it.message}" })

fun validatePropertyDefinition(definition: PropertyDefinition): PropertyValidationResult = validateProperty(
    propertyId = definition.id.value,
    inputs = definition.inputs,
    predicate = definition.predicate,
    precondition = definition.precondition,
)

fun validatePropertyManifest(manifest: PropertyManifest): PropertyValidationResult {
    val diagnostics = mutableListOf<ValidationDiagnostic>()
    if (manifest.schemaVersion != PROPERTY_MANIFEST_SCHEMA_VERSION) {
        diagnostics += diagnostic(
            code = "manifest.schema.unsupported",
            message = "Unsupported property manifest schema version: ${manifest.schemaVersion}",
            path = "schemaVersion",
        )
    }
    diagnostics += validateProperty(
        propertyId = manifest.propertyId,
        inputs = manifest.inputs,
        predicate = manifest.predicate,
        precondition = manifest.precondition,
    ).diagnostics
    return diagnostics.toResult()
}

fun requireValid(result: PropertyValidationResult) {
    if (!result.isValid) {
        throw InvalidPropertyDefinitionException(result)
    }
}

private fun validateProperty(
    propertyId: String,
    inputs: List<PropertyInput>,
    predicate: TypeScriptEntryPoint,
    precondition: TypeScriptEntryPoint?,
): PropertyValidationResult {
    val diagnostics = mutableListOf<ValidationDiagnostic>()
    if (!isCanonicalPropertyId(propertyId)) {
        diagnostics += diagnostic("property.id.invalid", "Invalid property ID", "propertyId")
    }
    if (inputs.isEmpty()) {
        diagnostics += diagnostic("property.inputs.empty", "A property requires at least one input", "inputs")
    }

    val firstInputByName = mutableMapOf<String, Int>()
    inputs.forEachIndexed { index, input ->
        val path = "inputs[$index]"
        if (!isJavaScriptIdentifier(input.name)) {
            diagnostics += diagnostic("input.name.invalid", "Invalid input name", "$path.name")
        }
        if (firstInputByName.putIfAbsent(input.name, index) != null) {
            diagnostics += diagnostic("input.name.duplicate", "Duplicate input name: ${input.name}", path)
        }
        validateDomain(input.domain, "$path.domain", diagnostics)
    }

    validateEntryPoint(predicate, "predicate", diagnostics)
    precondition?.let { validateEntryPoint(it, "precondition", diagnostics) }
    return diagnostics.toResult()
}

private fun validateDomain(
    domain: PropertyDomain,
    path: String,
    diagnostics: MutableList<ValidationDiagnostic>,
) {
    when (domain) {
        BooleanDomain -> {
            Unit
        }

        is IntegerDomain -> {
            if (domain.min > domain.max) {
                diagnostics += diagnostic("domain.integer.bounds", "Integer minimum exceeds maximum", path)
            }
        }

        is NumberDomain -> {
            validateNumberDomain(domain, path, diagnostics)
        }

        is StringDomain -> {
            validateLengths(
                minLength = domain.minLength,
                maxLength = domain.maxLength,
                code = "domain.string.length",
                description = "String",
                path = path,
                diagnostics = diagnostics,
            )
        }

        is ConstantDomain -> {
            if (domain.value is JsConcreteValue.Array) {
                diagnostics += diagnostic(
                    "domain.constant.unsupported",
                    "Constant domains support JavaScript primitives only",
                    path,
                )
            }
            validateJsConcreteValue(domain.value, "$path.value", diagnostics)
        }
        is OptionalDomain -> {
            if (domain.nil != JsConcreteValue.Undefined && domain.nil != JsConcreteValue.Null) {
                diagnostics += diagnostic(
                    "domain.optional.nil",
                    "Optional nil must be null or undefined",
                    "$path.nil",
                )
            }
            validateJsConcreteValue(domain.nil, "$path.nil", diagnostics)
            validateDomain(domain.value, "$path.value", diagnostics)
        }

        is TupleDomain -> {
            if (domain.elements.isEmpty()) {
                diagnostics += diagnostic("domain.tuple.empty", "Tuple domain must not be empty", path)
            }
            domain.elements.forEachIndexed { index, element ->
                validateDomain(element, "$path.elements[$index]", diagnostics)
            }
        }

        is ArrayDomain -> {
            validateLengths(
                minLength = domain.minLength,
                maxLength = domain.maxLength,
                code = "domain.array.length",
                description = "Array",
                path = path,
                diagnostics = diagnostics,
            )
            validateDomain(domain.element, "$path.element", diagnostics)
        }
    }
}

private fun validateNumberDomain(
    domain: NumberDomain,
    path: String,
    diagnostics: MutableList<ValidationDiagnostic>,
) {
    val minValid = validateJsNumber(domain.min, "$path.min", diagnostics)
    val maxValid = validateJsNumber(domain.max, "$path.max", diagnostics)
    if (domain.min.value == JsNumberKind.NAN) {
        diagnostics += diagnostic("domain.number.bound.nan", "Number minimum must not be NaN", "$path.min")
    }
    if (domain.max.value == JsNumberKind.NAN) {
        diagnostics += diagnostic("domain.number.bound.nan", "Number maximum must not be NaN", "$path.max")
    }
    if (minValid && maxValid && domain.min.value != JsNumberKind.NAN && domain.max.value != JsNumberKind.NAN &&
        domain.min.toDouble() > domain.max.toDouble()
    ) {
        diagnostics += diagnostic("domain.number.bounds", "Number minimum exceeds maximum", path)
    }

    val bounded = domain.min != JsNumber.negativeInfinity() || domain.max != JsNumber.positiveInfinity()
    if (bounded && domain.allowNaN) {
        diagnostics += diagnostic(
            "domain.number.nan-bounded",
            "Bounded number domains must exclude NaN",
            "$path.allowNaN",
        )
    }
}

private fun validateJsConcreteValue(
    value: JsConcreteValue,
    path: String,
    diagnostics: MutableList<ValidationDiagnostic>,
) {
    if (value is JsConcreteValue.Number) {
        validateJsNumber(value.number, path, diagnostics)
    }
    if (value is JsConcreteValue.Array) {
        value.elements.forEachIndexed { index, element ->
            validateJsConcreteValue(element, "$path.elements[$index]", diagnostics)
        }
    }
}

private fun validateJsNumber(
    number: JsNumber,
    path: String,
    diagnostics: MutableList<ValidationDiagnostic>,
): Boolean {
    val valid = when (number.value) {
        JsNumberKind.FINITE -> number.bits?.matches(FINITE_NUMBER_BITS_REGEX) == true
        else -> number.bits == null
    }
    if (!valid) {
        diagnostics += diagnostic(
            "js-number.encoding.invalid",
            "Invalid tagged JavaScript number encoding",
            path,
        )
    }
    return valid
}

private fun validateLengths(
    minLength: Int,
    maxLength: Int,
    code: String,
    description: String,
    path: String,
    diagnostics: MutableList<ValidationDiagnostic>,
) {
    if (minLength < 0 || maxLength < 0 || minLength > maxLength) {
        diagnostics += diagnostic(code, "$description length bounds are invalid", path)
    }
}

private fun validateEntryPoint(
    entryPoint: TypeScriptEntryPoint,
    path: String,
    diagnostics: MutableList<ValidationDiagnostic>,
) {
    if (!isProjectRelativePosixPath(entryPoint.module)) {
        diagnostics += diagnostic("entrypoint.module.invalid", "Invalid TypeScript module path", "$path.module")
    }
    if (!isJavaScriptIdentifier(entryPoint.exportName)) {
        diagnostics += diagnostic("entrypoint.export.invalid", "Invalid TypeScript export name", "$path.exportName")
    }
}

private fun isProjectRelativePosixPath(path: String): Boolean =
    path.isNotBlank() &&
        !path.startsWith('/') &&
        '\\' !in path &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }

private fun isJavaScriptIdentifier(value: String): Boolean {
    if (value.isEmpty()) return false
    var index = 0
    var first = true
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val valid = if (first) {
            codePoint == '$'.code || codePoint == '_'.code || Character.isUnicodeIdentifierStart(codePoint)
        } else {
            codePoint == '$'.code ||
                codePoint == '_'.code ||
                codePoint == ZERO_WIDTH_NON_JOINER ||
                codePoint == ZERO_WIDTH_JOINER ||
                Character.isUnicodeIdentifierPart(codePoint)
        }
        if (!valid) return false
        first = false
        index += Character.charCount(codePoint)
    }
    return true
}

private fun MutableList<ValidationDiagnostic>.toResult(): PropertyValidationResult =
    sortedWith(compareBy(ValidationDiagnostic::path, ValidationDiagnostic::code))
        .let(::PropertyValidationResult)

private fun diagnostic(code: String, message: String, path: String) = ValidationDiagnostic(code, message, path)

private val FINITE_NUMBER_BITS_REGEX = Regex("[0-9a-f]{16}")
private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D
