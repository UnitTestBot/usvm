package org.usvm.ts.calls

import io.ksmt.expr.KBitVec16Value
import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.initializeArrayLength
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsState
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.mapping.EtsLexicalEnvironmentBinding
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.util.EtsHierarchy
import org.usvm.util.getAllFields
import org.usvm.util.markDenseInputArray
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.mkStringFromCodeUnits

internal fun PropertyDomain.isSupportedCallsSymbolicDomain(): Boolean = when (this) {
    BooleanDomain, is NumberDomain, is StringDomain -> true
    is ArrayDomain -> element == BooleanDomain || element is NumberDomain
    is ObjectDomain -> fields.values.all { it.isSupportedCallsSymbolicDomain() }
    else -> false
}

internal fun callsSymbolicInputPreflight(inputs: List<PropertyInput>): String? {
    val unsupportedInput = inputs.firstOrNull { input -> !input.domain.isSupportedCallsSymbolicDomain() }
        ?: return null

    return "Unsupported symbolic input domain for ${unsupportedInput.name}: ${unsupportedInput.domain}"
}

internal fun callsSymbolicInputTypePreflight(
    inputs: List<PropertyInput>,
    bindings: List<EtsInputBinding>,
    scene: EtsScene,
): String? {
    val hierarchy = EtsHierarchy(scene)
    val bindingsByName = bindings.associateBy(EtsInputBinding::propertyInputName)

    return inputs.firstNotNullOfOrNull { input ->
        val parameterType = bindingsByName[input.name]?.parameter?.type
            ?: return@firstNotNullOfOrNull "Missing EtsIR binding for symbolic input ${input.name}"
        input.domain.objectTypeDiagnostic(parameterType, input.name, hierarchy)
    }
}

private fun PropertyDomain.objectTypeDiagnostic(
    type: EtsType?,
    path: String,
    hierarchy: EtsHierarchy,
): String? {
    if (this !is ObjectDomain) return null

    val objectType = type as? EtsRefType
        ?: return "Object domain at $path requires a reference-typed EtsIR value: $type"

    return fields.entries.firstNotNullOfOrNull { (name, fieldDomain) ->
        if (fieldDomain !is ObjectDomain) return@firstNotNullOfOrNull null

        val fieldType = objectFieldType(objectType, name, hierarchy)
            ?: return@firstNotNullOfOrNull "Cannot resolve EtsIR type of object field $path.$name in $objectType"
        fieldDomain.objectTypeDiagnostic(fieldType, "$path.$name", hierarchy)
    }
}

private fun objectFieldType(type: EtsRefType, name: String, hierarchy: EtsHierarchy): EtsType? =
    hierarchy.classesForType(type)
        .flatMap { clazz -> clazz.getAllFields(hierarchy) }
        .filter { field -> field.name == name }
        .distinctBy { field -> field.signature }
        .singleOrNull()
        ?.type

internal class CallsSymbolicInputs(
    inputs: List<PropertyInput>,
    bindings: List<EtsInputBinding>,
    private val lexicalEnvironment: EtsLexicalEnvironmentBinding?,
) {
    private val boundInputs: List<Pair<PropertyInput, EtsInputBinding>>
    private var snapshots: List<CallsInputSnapshot>? = null

    init {
        require(inputs.size == bindings.size) { "Property inputs do not match mapped EtsIR bindings" }
        val bindingsByName = bindings.associateBy(EtsInputBinding::propertyInputName)
        require(bindingsByName.size == bindings.size) { "Mapped EtsIR input names must be unique" }

        boundInputs = inputs.map { input ->
            input to requireNotNull(bindingsByName[input.name]) {
                "Missing EtsIR binding for property input ${input.name}"
            }
        }
    }

    fun initialize(state: TsState) {
        check(snapshots == null) { "Calls symbolic inputs were initialized more than once" }
        lexicalEnvironment?.let(state::initializeLexicalEnvironment)
        val hierarchy = EtsHierarchy(state.ctx.scene)
        val initializedSnapshots = boundInputs.map { (input, binding) ->
            state.initializeInput(
                stackSlot = binding.stackSlot,
                domain = input.domain,
                parameterType = binding.parameter.type,
                hierarchy = hierarchy,
            )
        }
        initializedSnapshots.forEach { snapshot -> snapshot.markDenseInputs(state) }
        snapshots = initializedSnapshots
    }

    fun sortOverride(ctx: TsContext, stackSlot: Int): USort? {
        if (lexicalEnvironment?.stackSlot == stackSlot) return ctx.addressSort

        val domain = boundInputs.singleOrNull { (_, binding) -> binding.stackSlot == stackSlot }?.first?.domain
            ?: return null

        return with(ctx) {
            when (domain) {
                BooleanDomain -> boolSort
                is NumberDomain -> fp64Sort
                is StringDomain, is ArrayDomain, is ObjectDomain -> addressSort
                else -> null
            }
        }
    }

    fun resolve(state: TsState): List<JsConcreteValue> {
        val initializedSnapshots = checkNotNull(snapshots) { "Calls symbolic inputs were not initialized" }
        val model = state.models.single()

        return initializedSnapshots.map { snapshot -> snapshot.resolve(model) }
    }
}

private fun TsState.initializeLexicalEnvironment(binding: EtsLexicalEnvironmentBinding): Unit = with(ctx) {
    val environmentType = binding.parameter.type as? EtsLexicalEnvType
        ?: error("Mapped lexical environment does not have a lexical-environment type")
    val environmentRef = allocateConcreteRef()
    for (captured in environmentType.closures) {
        initializeBuiltinCapture(captured = captured, environmentRef = environmentRef)
    }
    memory.write(
        mkRegisterStackLValue(addressSort, binding.stackSlot),
        environmentRef.asExpr(addressSort),
        guard = trueExpr,
    )
    saveSortForLocal(binding.stackSlot, addressSort)
}

private fun TsState.initializeBuiltinCapture(
    captured: EtsLocal,
    environmentRef: UConcreteHeapRef,
): Unit = with(ctx) {
    val value = when (captured.name) {
        "Infinity" -> mkFpInf(signBit = false, sort = fp64Sort)
        "NaN" -> mkFp64NaN()
        else -> memory.allocConcrete(
            EtsUnclearRefType(
                name = captured.name,
                typeParameters = emptyList(),
            ),
        ).asExpr(addressSort)
    }
    val expectedSort = typeToSort(captured.type).let { sort ->
        if (sort == unresolvedSort) addressSort else sort
    }
    require(value.sort == expectedSort) {
        "Builtin capture ${captured.name} has sort ${value.sort}, expected $expectedSort"
    }
    memory.write(
        mkFieldLValue(expectedSort, environmentRef, captured.name),
        value.asExpr(expectedSort),
        guard = trueExpr,
    )
}

private fun TsState.initializeInput(
    stackSlot: Int,
    domain: PropertyDomain,
    parameterType: EtsType,
    hierarchy: EtsHierarchy,
): CallsInputSnapshot = with(ctx) {
    val initialized = initializeValue(domain = domain, parameterType = parameterType, hierarchy = hierarchy)
    memory.write(
        mkRegisterStackLValue(initialized.sort, stackSlot),
        initialized.value.asExpr(initialized.sort),
        guard = trueExpr,
    )
    saveSortForLocal(stackSlot, initialized.sort)

    initialized.snapshot
}

private data class InitializedCallsValue(
    val sort: USort,
    val value: UExpr<*>,
    val snapshot: CallsInputSnapshot,
)

private fun TsState.initializeValue(
    domain: PropertyDomain,
    parameterType: EtsType?,
    hierarchy: EtsHierarchy,
): InitializedCallsValue = with(ctx) {
    when (domain) {
        BooleanDomain -> {
            val value: UBoolExpr = makeSymbolicPrimitive(boolSort)
            InitializedCallsValue(boolSort, value, BooleanInputSnapshot(value))
        }

        is NumberDomain -> {
            val value: UExpr<KFp64Sort> = makeSymbolicPrimitive(fp64Sort)
            pathConstraints += numberDomainConstraint(value, domain)
            InitializedCallsValue(fp64Sort, value, NumberInputSnapshot(value))
        }

        is StringDomain -> {
            initializeStringValue(domain)
        }

        is ArrayDomain -> {
            initializeArrayValue(domain = domain, hierarchy = hierarchy)
        }

        is ObjectDomain -> {
            initializeObjectValue(domain = domain, parameterType = parameterType, hierarchy = hierarchy)
        }

        else -> {
            error("Unsupported calls symbolic domain: $domain")
        }
    }
}

private fun TsState.initializeObjectValue(
    domain: ObjectDomain,
    parameterType: EtsType?,
    hierarchy: EtsHierarchy,
): InitializedCallsValue = with(ctx) {
    val runtimeType = parameterType as? EtsRefType
        ?: error("Object domain requires a reference-typed EtsIR parameter: $parameterType")
    val objectRef = memory.allocConcrete(runtimeType)
    val fields = domain.fields.mapValues { (name, fieldDomain) ->
        val fieldType = when (fieldDomain) {
            is ObjectDomain -> objectFieldType(runtimeType, name, hierarchy)
                ?: error("Cannot resolve EtsIR type of object field $name in $runtimeType")

            else -> null
        }
        val initialized = initializeValue(domain = fieldDomain, parameterType = fieldType, hierarchy = hierarchy)

        memory.write(
            mkFieldLValue(initialized.sort, objectRef, name),
            initialized.value.asExpr(initialized.sort),
            guard = trueExpr,
        )
        initialized.snapshot
    }

    InitializedCallsValue(addressSort, objectRef, ObjectInputSnapshot(fields))
}

private fun TsState.initializeStringValue(
    domain: StringDomain,
): InitializedCallsValue = with(ctx) {
    val length: UExpr<TsSizeSort> = makeSymbolicPrimitive(sizeSort)
    val codeUnits: List<UExpr<KBv16Sort>> = List(domain.maxLength) { makeSymbolicPrimitive(bv16Sort) }

    constrainLength(length = length, minLength = domain.minLength, maxLength = domain.maxLength)
    val stringRef = mkStringFromCodeUnits(length = length, codeUnits = codeUnits)

    InitializedCallsValue(addressSort, stringRef, StringInputSnapshot(length = length, codeUnits = codeUnits))
}

private fun TsState.initializeArrayValue(
    domain: ArrayDomain,
    hierarchy: EtsHierarchy,
): InitializedCallsValue = with(ctx) {
    val runtimeType = when (domain.element) {
        BooleanDomain -> EtsArrayType(EtsBooleanType, dimensions = 1)
        is NumberDomain -> EtsArrayType(EtsNumberType, dimensions = 1)
        else -> error("Unsupported calls symbolic array element domain: ${domain.element}")
    }
    val descriptor = arrayDescriptorOf(runtimeType)
    val arrayRef = memory.allocConcrete(descriptor)
    val length: UExpr<TsSizeSort> = makeSymbolicPrimitive(sizeSort)

    constrainLength(length = length, minLength = domain.minLength, maxLength = domain.maxLength)
    memory.initializeArrayLength(
        arrayHeapRef = arrayRef,
        type = descriptor,
        sizeSort = sizeSort,
        count = length,
    )
    val elements = List(domain.maxLength) { index ->
        val initialized = initializeValue(
            domain = domain.element,
            parameterType = runtimeType.elementType,
            hierarchy = hierarchy,
        )
        val liveIndex = mkBvSignedLessExpr(mkBv(index), length)
        memory.write(
            mkArrayIndexLValue(initialized.sort, arrayRef, mkBv(index), runtimeType),
            initialized.value.asExpr(initialized.sort),
            guard = liveIndex,
        )
        initialized.snapshot
    }
    val snapshot = ArrayInputSnapshot(
        length = length,
        elements = elements,
        arrayRef = arrayRef,
        runtimeType = runtimeType,
    )
    InitializedCallsValue(addressSort, arrayRef, snapshot)
}

private fun TsState.constrainLength(
    length: UExpr<TsSizeSort>,
    minLength: Int,
    maxLength: Int,
) = with(ctx) {
    pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(minLength))
    pathConstraints += mkBvSignedLessOrEqualExpr(length, mkBv(maxLength))
}

private fun TsState.numberDomainConstraint(
    value: UExpr<KFp64Sort>,
    domain: NumberDomain,
): UBoolExpr = with(ctx) {
    val lowerBound = mkFp64(domain.min.toDouble())
    val upperBound = mkFp64(domain.max.toDouble())
    val inBounds = mkAnd(
        mkFpLessOrEqualExpr(lowerBound, value),
        mkFpLessOrEqualExpr(value, upperBound),
    )

    if (domain.allowNaN) mkOr(mkFpIsNaNExpr(value), inBounds) else inBounds
}

private sealed interface CallsInputSnapshot {
    fun resolve(model: UModelBase<EtsType>): JsConcreteValue

    fun markDenseInputs(state: TsState) {}
}

private data class BooleanInputSnapshot(
    val value: UBoolExpr,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.Boolean(model.eval(value).toConcreteBoolValue())
}

private data class NumberInputSnapshot(
    val value: UExpr<KFp64Sort>,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.number(model.eval(value).extractDouble())
}

private data class StringInputSnapshot(
    val length: UExpr<TsSizeSort>,
    val codeUnits: List<UExpr<KBv16Sort>>,
) : CallsInputSnapshot {
    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue {
        val concreteLength = model.eval(length).extractInt()
        require(concreteLength in 0..codeUnits.size) { "Resolved string length is outside its symbolic domain" }
        val value = buildString(concreteLength) {
            codeUnits.take(concreteLength).forEach { codeUnit ->
                val concreteCodeUnit = model.eval(codeUnit) as KBitVec16Value
                append((concreteCodeUnit.shortValue.toInt() and UTF16_CODE_UNIT_MASK).toChar())
            }
        }

        return JsConcreteValue.String(value)
    }
}

private data class ArrayInputSnapshot(
    val length: UExpr<TsSizeSort>,
    val elements: List<CallsInputSnapshot>,
    val arrayRef: UConcreteHeapRef,
    val runtimeType: EtsArrayType,
) : CallsInputSnapshot {
    override fun markDenseInputs(state: TsState) {
        state.markDenseInputArray(array = arrayRef, type = runtimeType)
        elements.forEach { element -> element.markDenseInputs(state) }
    }

    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue {
        val concreteLength = model.eval(length).extractInt()
        require(concreteLength in 0..elements.size) { "Resolved array length is outside its symbolic domain" }

        return JsConcreteValue.Array(elements.take(concreteLength).map { element -> element.resolve(model) })
    }
}

private data class ObjectInputSnapshot(
    val fields: Map<String, CallsInputSnapshot>,
) : CallsInputSnapshot {
    override fun markDenseInputs(state: TsState) {
        fields.values.forEach { field -> field.markDenseInputs(state) }
    }

    override fun resolve(model: UModelBase<EtsType>): JsConcreteValue =
        JsConcreteValue.Object(fields.mapValues { (_, snapshot) -> snapshot.resolve(model) })
}

private const val UTF16_CODE_UNIT_MASK = 0xffff
