package org.usvm.ts.pbt.validation

import org.usvm.ts.pbt.PbtDiagnosticCode
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

/**
 * One deterministic validation failure.
 *
 * @property code stable machine-readable diagnostic code
 * @property message human-readable description of the invalid value
 * @property path location of the invalid value in the property model
 */
data class ValidationDiagnostic(
    val code: String,
    val message: String,
    val path: String,
)

/** Ordered validation diagnostics and their derived validity state. */
data class PropertyValidationResult(val diagnostics: List<ValidationDiagnostic>) {
    val isValid: Boolean
        get() = diagnostics.isEmpty()
}

/** Thrown when an operation requires a valid property but receives [result] with diagnostics. */
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
    return validateProperty(
        propertyId = manifest.propertyId,
        inputs = manifest.inputs,
        predicate = manifest.predicate,
        precondition = manifest.precondition,
    )
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
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.PROPERTY_ID_INVALID,
            message = "Invalid property ID",
            path = "propertyId",
        )
    }
    if (inputs.isEmpty()) {
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.PROPERTY_INPUTS_EMPTY,
            message = "A property requires at least one input",
            path = "inputs",
        )
    }

    val firstInputByName = mutableMapOf<String, Int>()
    inputs.forEachIndexed { index, input ->
        val path = "inputs[$index]"
        if (!isJavaScriptIdentifier(input.name)) {
            diagnostics += diagnostic(
                code = PbtDiagnosticCode.INPUT_NAME_INVALID,
                message = "Invalid input name",
                path = "$path.name",
            )
        }
        if (firstInputByName.putIfAbsent(input.name, index) != null) {
            diagnostics += diagnostic(
                code = PbtDiagnosticCode.INPUT_NAME_DUPLICATE,
                message = "Duplicate input name: ${input.name}",
                path = path,
            )
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
                diagnostics += diagnostic(
                    code = PbtDiagnosticCode.DOMAIN_INTEGER_BOUNDS,
                    message = "Integer minimum exceeds maximum",
                    path = path,
                )
            }
        }

        is NumberDomain -> {
            validateNumberDomain(domain, path, diagnostics)
        }

        is StringDomain -> {
            validateLengths(
                minLength = domain.minLength,
                maxLength = domain.maxLength,
                code = PbtDiagnosticCode.DOMAIN_STRING_LENGTH,
                description = "String",
                path = path,
                diagnostics = diagnostics,
            )
        }

        is ConstantDomain -> {
            if (domain.value is JsConcreteValue.Array) {
                diagnostics += diagnostic(
                    code = PbtDiagnosticCode.DOMAIN_CONSTANT_UNSUPPORTED,
                    message = "Constant domains support JavaScript primitives only",
                    path = path,
                )
            }
            validateJsConcreteValue(domain.value, "$path.value", diagnostics)
        }
        is OptionalDomain -> {
            if (domain.nil != JsConcreteValue.Undefined && domain.nil != JsConcreteValue.Null) {
                diagnostics += diagnostic(
                    code = PbtDiagnosticCode.DOMAIN_OPTIONAL_NIL,
                    message = "Optional nil must be null or undefined",
                    path = "$path.nil",
                )
            }
            validateJsConcreteValue(domain.nil, "$path.nil", diagnostics)
            validateDomain(domain.value, "$path.value", diagnostics)
        }

        is TupleDomain -> {
            if (domain.elements.isEmpty()) {
                diagnostics += diagnostic(
                    code = PbtDiagnosticCode.DOMAIN_TUPLE_EMPTY,
                    message = "Tuple domain must not be empty",
                    path = path,
                )
            }
            domain.elements.forEachIndexed { index, element ->
                validateDomain(element, "$path.elements[$index]", diagnostics)
            }
        }

        is ArrayDomain -> {
            validateLengths(
                minLength = domain.minLength,
                maxLength = domain.maxLength,
                code = PbtDiagnosticCode.DOMAIN_ARRAY_LENGTH,
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
    val minimumEncodingIsValid = validateJsNumber(domain.min, "$path.min", diagnostics)
    val maximumEncodingIsValid = validateJsNumber(domain.max, "$path.max", diagnostics)

    if (domain.min.value == JsNumberKind.NAN) {
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.DOMAIN_NUMBER_BOUND_NAN,
            message = "Number minimum must not be NaN",
            path = "$path.min",
        )
    }
    if (domain.max.value == JsNumberKind.NAN) {
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.DOMAIN_NUMBER_BOUND_NAN,
            message = "Number maximum must not be NaN",
            path = "$path.max",
        )
    }

    val encodingsAreValid = minimumEncodingIsValid && maximumEncodingIsValid
    val boundsAreNotNaN = domain.min.value != JsNumberKind.NAN && domain.max.value != JsNumberKind.NAN
    val boundsCanBeCompared = encodingsAreValid && boundsAreNotNaN
    val minimumExceedsMaximum = boundsCanBeCompared && domain.min.toDouble() > domain.max.toDouble()
    if (minimumExceedsMaximum) {
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.DOMAIN_NUMBER_BOUNDS,
            message = "Number minimum exceeds maximum",
            path = path,
        )
    }

    val bounded = domain.min != JsNumber.negativeInfinity() || domain.max != JsNumber.positiveInfinity()
    if (bounded && domain.allowNaN) {
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.DOMAIN_NUMBER_NAN_BOUNDED,
            message = "Bounded number domains must exclude NaN",
            path = "$path.allowNaN",
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
            code = PbtDiagnosticCode.JS_NUMBER_ENCODING_INVALID,
            message = "Invalid tagged JavaScript number encoding",
            path = path,
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
        diagnostics += diagnostic(
            code = code,
            message = "$description length bounds are invalid",
            path = path,
        )
    }
}

private fun validateEntryPoint(
    entryPoint: TypeScriptEntryPoint,
    path: String,
    diagnostics: MutableList<ValidationDiagnostic>,
) {
    if (!isProjectRelativePosixPath(entryPoint.module)) {
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.ENTRY_POINT_MODULE_INVALID,
            message = "Invalid TypeScript module path",
            path = "$path.module",
        )
    }
    if (!isJavaScriptIdentifier(entryPoint.exportName)) {
        diagnostics += diagnostic(
            code = PbtDiagnosticCode.ENTRY_POINT_EXPORT_INVALID,
            message = "Invalid TypeScript export name",
            path = "$path.exportName",
        )
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
            isJavaScriptIdentifierStart(codePoint)
        } else {
            isJavaScriptIdentifierPart(codePoint)
        }
        if (!valid) return false

        first = false
        index += Character.charCount(codePoint)
    }

    return true
}

private fun isJavaScriptIdentifierStart(codePoint: Int): Boolean =
    codePoint == '$'.code ||
        codePoint == '_'.code ||
        Character.isUnicodeIdentifierStart(codePoint)

private fun isJavaScriptIdentifierPart(codePoint: Int): Boolean =
    isJavaScriptIdentifierStart(codePoint) ||
        codePoint == ZERO_WIDTH_NON_JOINER_CODE_POINT ||
        codePoint == ZERO_WIDTH_JOINER_CODE_POINT ||
        Character.isUnicodeIdentifierPart(codePoint)

private fun MutableList<ValidationDiagnostic>.toResult(): PropertyValidationResult {
    val orderedDiagnostics = sortedWith(compareBy(ValidationDiagnostic::path, ValidationDiagnostic::code))
    return PropertyValidationResult(orderedDiagnostics)
}

private fun diagnostic(code: String, message: String, path: String) = ValidationDiagnostic(
    code = code,
    message = message,
    path = path,
)

private val FINITE_NUMBER_BITS_REGEX = Regex("[0-9a-f]{16}")

// ECMAScript permits these otherwise invisible Unicode characters after the first identifier character.
private const val ZERO_WIDTH_NON_JOINER_CODE_POINT = 0x200C
private const val ZERO_WIDTH_JOINER_CODE_POINT = 0x200D
