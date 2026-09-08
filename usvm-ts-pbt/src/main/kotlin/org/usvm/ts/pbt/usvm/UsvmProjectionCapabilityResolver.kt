package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanLiteralType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.aggregateProjectionCapabilities
import org.usvm.ts.pbt.backend.classifyPropertyCapability
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsEntryPointTarget
import org.usvm.ts.pbt.mapping.EtsMappingResult
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain

/** Calculates USVM fidelity without constructing or mutating a symbolic state. */
class UsvmProjectionCapabilityResolver {
    fun resolve(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
        options: UsvmProjectionOptions = UsvmProjectionOptions(),
    ): UsvmPropertyProjectionCapability {
        val predicateCapability = entryPointCapability(
            mapping = mapping.predicate,
            path = "predicate",
            nonExactCode = PbtDiagnosticCode.USVM_PREDICATE_MAPPING_NON_EXACT,
        )
        val predicateTarget = mapping.predicate.exactTargetOrNull()
        val predicateExecutionCapability = if (manifest.predicate.executionKind == ExecutionKind.SYNC) {
            exact()
        } else {
            unsupported(
                code = PbtDiagnosticCode.USVM_PREDICATE_ASYNC,
                message = "Asynchronous TypeScript predicates are not supported by USVM search",
                path = "predicate",
            )
        }
        val inputCapabilities = manifest.inputs.mapIndexed { index, input ->
            val path = "inputs[$index].domain"
            val parameterType = predicateTarget
                ?.bindings
                ?.inputs
                ?.getOrNull(index)
                ?.parameter
                ?.type
            val capability = if (parameterType == null) {
                unsupported(
                    code = PbtDiagnosticCode.USVM_INPUT_BINDING_UNAVAILABLE,
                    message = "An exact EtsIR input binding is required",
                    path = path,
                )
            } else {
                domainCapabilityForProjector(input.domain, parameterType, path, options)
            }

            UsvmInputProjectionCapability(
                inputName = input.name,
                path = path,
                capability = capability,
            )
        }
        val preconditionCapability = preconditionCapability(manifest, mapping, options)
        val propertyIdCapability = if (mapping.propertyId.value == manifest.propertyId) {
            exact()
        } else {
            unsupported(
                code = PbtDiagnosticCode.USVM_MAPPING_PROPERTY_ID_MISMATCH,
                message = "The property manifest and EtsIR mapping artifact have different IDs",
                path = "propertyId",
            )
        }
        val symbolicComponents = buildList {
            add(propertyIdCapability)
            add(predicateCapability)
            add(predicateExecutionCapability)
            addAll(inputCapabilities.map { it.capability })
            add(preconditionCapability)
        }
        val symbolicCapability = aggregateProjectionCapabilities(symbolicComponents)

        return UsvmPropertyProjectionCapability(
            inputs = inputCapabilities,
            precondition = preconditionCapability,
            symbolic = symbolicCapability,
            property = classifyPropertyCapability(concreteCapability, symbolicCapability),
        )
    }

    private fun preconditionCapability(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        options: UsvmProjectionOptions,
    ): ProjectionCapability {
        val declaredPrecondition = manifest.precondition ?: return exact()
        if (declaredPrecondition.executionKind == ExecutionKind.ASYNC) {
            return unsupported(
                code = PbtDiagnosticCode.USVM_PRECONDITION_ASYNC,
                message = "Asynchronous TypeScript preconditions are not supported by USVM projection",
                path = "precondition",
            )
        }

        val mappedPrecondition = mapping.precondition
            ?: return unsupported(
                code = PbtDiagnosticCode.USVM_PRECONDITION_MAPPING_UNAVAILABLE,
                message = "The declared precondition has no EtsIR mapping",
                path = "precondition",
            )
        val mappingCapability = entryPointCapability(
            mapping = mappedPrecondition,
            path = "precondition",
            nonExactCode = PbtDiagnosticCode.USVM_PRECONDITION_MAPPING_NON_EXACT,
        )
        val target = mappedPrecondition.exactTargetOrNull()
            ?: return mappingCapability
        val inputCapabilities = manifest.inputs.mapIndexed { index, input ->
            val parameterType = target.bindings.inputs
                .getOrNull(index)
                ?.parameter
                ?.type

            if (parameterType == null) {
                unsupported(
                    code = PbtDiagnosticCode.USVM_PRECONDITION_BINDING_UNAVAILABLE,
                    message = "An exact EtsIR precondition input binding is required",
                    path = "precondition",
                )
            } else {
                domainCapabilityForProjector(input.domain, parameterType, "inputs[$index].domain", options)
            }
        }

        return aggregateProjectionCapabilities(listOf(mappingCapability) + inputCapabilities)
    }

    private fun entryPointCapability(
        mapping: EtsMappingResult<EtsEntryPointTarget>,
        path: String,
        nonExactCode: String,
    ): ProjectionCapability = if (mapping.status == EtsMappingStatus.EXACT && mapping.targets.size == 1) {
        exact()
    } else {
        unsupported(
            code = nonExactCode,
            message = "USVM projection requires exactly one EtsIR target, got ${mapping.status}",
            path = path,
        )
    }

    internal fun domainCapabilityForProjector(
        domain: PropertyDomain,
        etsType: EtsType,
        path: String,
        options: UsvmProjectionOptions,
    ): ProjectionCapability {
        if (!domainCompatibilityForProjector(domain, etsType)) {
            return unsupported(
                code = PbtDiagnosticCode.USVM_DOMAIN_TYPE_UNSUPPORTED,
                message = "Domain ${domain::class.simpleName} cannot be projected into EtsIR type $etsType",
                path = path,
            )
        }

        return when (domain) {
            BooleanDomain, is IntegerDomain, is NumberDomain -> exact()
            is StringDomain -> approximateString(path)
            is ConstantDomain -> constantCapability(domain.value, path)
            is OptionalDomain -> domainCapabilityForProjector(
                domain = domain.value,
                etsType = nestedOptionalType(domain, etsType),
                path = "$path.value",
                options = options,
            )

            is TupleDomain -> tupleCapability(domain, etsType, path, options)
            is ArrayDomain -> arrayCapability(domain, etsType, path, options)
        }
    }

    private fun tupleCapability(
        domain: TupleDomain,
        etsType: EtsType,
        path: String,
        options: UsvmProjectionOptions,
    ): ProjectionCapability {
        if (domain.elements.size > options.maxSymbolicCollectionLength) {
            return collectionTooLarge(domain.elements.size, options, path)
        }

        val elementTypes = when (etsType) {
            is EtsTupleType -> etsType.types
            is EtsArrayType -> List(domain.elements.size) { etsType.elementType }
            else -> return unsupported(
                code = PbtDiagnosticCode.USVM_DOMAIN_TYPE_UNSUPPORTED,
                message = "Tuple domain requires an EtsIR tuple or array type",
                path = path,
            )
        }
        val elementCapabilities = domain.elements.mapIndexed { index, element ->
            domainCapabilityForProjector(element, elementTypes[index], "$path.elements[$index]", options)
        }

        return aggregateProjectionCapabilities(elementCapabilities)
    }

    private fun arrayCapability(
        domain: ArrayDomain,
        etsType: EtsType,
        path: String,
        options: UsvmProjectionOptions,
    ): ProjectionCapability {
        if (domain.maxLength > options.maxSymbolicCollectionLength) {
            return collectionTooLarge(domain.maxLength, options, path)
        }
        if ((etsType as EtsArrayType).elementType is EtsArrayType) {
            return unsupported(
                code = PbtDiagnosticCode.USVM_DOMAIN_ARRAY_NESTED_UNSUPPORTED,
                message = "Nested EtsIR arrays are not supported by the current TypeScript heap model",
                path = path,
            )
        }

        val elementType = etsType.elementType

        return domainCapabilityForProjector(domain.element, elementType, "$path.element", options)
    }

    private fun collectionTooLarge(
        actualLength: Int,
        options: UsvmProjectionOptions,
        path: String,
    ) = unsupported(
        code = PbtDiagnosticCode.USVM_DOMAIN_COLLECTION_TOO_LARGE,
        message = "Collection length $actualLength exceeds the symbolic cap " +
            options.maxSymbolicCollectionLength,
        path = path,
    )

    internal fun domainCompatibilityForProjector(domain: PropertyDomain, etsType: EtsType): Boolean = when (domain) {
        BooleanDomain -> etsType == EtsBooleanType || etsType is EtsBooleanLiteralType
        is IntegerDomain, is NumberDomain -> etsType == EtsNumberType || etsType is EtsNumberLiteralType
        is StringDomain -> etsType == EtsStringType || etsType is EtsStringLiteralType
        is ConstantDomain -> isConstantCompatible(domain.value, etsType)
        is OptionalDomain -> isOptionalCompatible(domain, etsType)
        is TupleDomain -> when (etsType) {
            is EtsTupleType -> {
                etsType.types.size == domain.elements.size &&
                    domain.elements.zip(etsType.types).all { (element, elementType) ->
                        domainCompatibilityForProjector(element, elementType)
                    }
            }

            is EtsArrayType -> {
                etsType.dimensions == 1 &&
                    domain.elements.all { domainCompatibilityForProjector(it, etsType.elementType) }
            }

            else -> false
        }

        is ArrayDomain -> {
            etsType is EtsArrayType &&
                etsType.dimensions == 1 &&
                domainCompatibilityForProjector(domain.element, etsType.elementType)
        }
    }

    private fun isConstantCompatible(value: JsConcreteValue, etsType: EtsType): Boolean = when (value) {
        is JsConcreteValue.Boolean -> etsType == EtsBooleanType || etsType is EtsBooleanLiteralType
        is JsConcreteValue.Number -> etsType == EtsNumberType || etsType is EtsNumberLiteralType
        is JsConcreteValue.String -> etsType == EtsStringType || etsType is EtsStringLiteralType
        JsConcreteValue.Null -> etsType == EtsNullType
        JsConcreteValue.Undefined -> etsType == EtsUndefinedType
        is JsConcreteValue.Array -> false
    }

    private fun isOptionalCompatible(domain: OptionalDomain, etsType: EtsType): Boolean {
        val union = etsType as? EtsUnionType ?: return false
        val nilType = nilType(domain.nil) ?: return false

        return union.types.any { it == nilType } &&
            union.types.any { domainCompatibilityForProjector(domain.value, it) }
    }

    private fun nestedOptionalType(domain: OptionalDomain, etsType: EtsType): EtsType {
        val union = etsType as EtsUnionType

        return union.types.first { domainCompatibilityForProjector(domain.value, it) }
    }

    private fun nilType(nil: JsConcreteValue): EtsType? = when (nil) {
        JsConcreteValue.Null -> EtsNullType
        JsConcreteValue.Undefined -> EtsUndefinedType
        else -> null
    }

    private fun constantCapability(value: JsConcreteValue, path: String): ProjectionCapability = when (value) {
        is JsConcreteValue.String -> approximateString(path)
        else -> exact()
    }

    private fun approximateString(path: String): ProjectionCapability {
        val diagnostic = CapabilityDiagnostic(
            code = PbtDiagnosticCode.USVM_DOMAIN_STRING_APPROXIMATE,
            message = "Over-approximation: USVM constrains string type and length, " +
                "but leaves UTF-16 contents unconstrained",
            path = path,
        )

        return ProjectionCapability(
            level = ProjectionLevel.APPROXIMATE,
            diagnostics = listOf(diagnostic),
        )
    }

    private fun unsupported(code: String, message: String, path: String): ProjectionCapability {
        val diagnostic = CapabilityDiagnostic(code = code, message = message, path = path)

        return ProjectionCapability(
            level = ProjectionLevel.UNSUPPORTED,
            diagnostics = listOf(diagnostic),
        )
    }

    private fun exact() = ProjectionCapability(level = ProjectionLevel.EXACT)
}

internal fun EtsMappingResult<EtsEntryPointTarget>.exactTargetOrNull(): EtsEntryPointTarget? =
    targets.singleOrNull()?.takeIf { status == EtsMappingStatus.EXACT }
