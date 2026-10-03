package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.initializeArrayLength
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.isUnmodifiedDenseInputArray
import org.usvm.util.mkArrayLengthLValue

/** Built-in Array algorithms implemented by ordinary TypeScript bodies. */
internal object TsArrayEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val CLASS_NAME = "ArrayModels"
    private const val PRIMITIVES_CLASS_NAME = "ArrayModelPrimitives"
    private const val RESOURCE_NAME = "/org/usvm/machine/call/models/ArrayModels.ts"
    private const val MAX_SOURCE_ARRAY_LENGTH = 16
    private const val MAX_VARIADIC_ARGUMENTS = 3
    private const val MAX_FILL_ARGUMENTS = 3

    private val baseArtifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = RESOURCE_NAME,
            sourceFileName = "ArrayModels.ts",
            entryPointClassName = CLASS_NAME,
            entryPointMethodName = "pop",
        )
    }

    private val arrayDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        with(state.ctx) {
            val receiver = inputs.firstOrNull()
            val staticType = call.receiver?.source?.type
            if (staticType == null || receiver?.sort != addressSort) {
                falseExpr
            } else {
                val array = receiver.asExpr(addressSort)
                val receiverType = state.arrayStorageType(array, staticType) as? EtsArrayType
                val searchElement = inputs.getOrNull(1)
                val searchRef = searchElement
                    ?.takeIf { it.sort == addressSort }
                    ?.asExpr(addressSort)
                if (
                    array.hasFakeValueBranch() || receiverType?.dimensions != 1 ||
                    searchRef?.hasFakeValueBranch() == true
                ) {
                    falseExpr
                } else {
                    val elementSort = typeToSort(receiverType.elementType)
                    val hasNoMissingSlots = array is UConcreteHeapRef &&
                        state.isUnmodifiedDenseInputArray(array, receiverType)
                    val excludesMissingSlot = when {
                        searchElement == mkUndefinedValue() &&
                            (
                                call.callee.name in setOf("indexOf", "lastIndexOf") ||
                                    elementSort == fp64Sort || elementSort == boolSort
                                ) -> mkBool(hasNoMissingSlots)

                        elementSort == fp64Sort && searchElement?.sort == fp64Sort -> {
                            val searchNumber = searchElement.asExpr(fp64Sort)
                            val zero = mkFp64(0.0)

                            mkOr(mkBool(hasNoMissingSlots), mkNot(mkFpEqualExpr(searchNumber, zero)))
                        }

                        elementSort == boolSort && searchElement?.sort == boolSort -> {
                            mkOr(mkBool(hasNoMissingSlots), searchElement.asExpr(boolSort))
                        }

                        else -> trueExpr
                    }

                    mkAnd(
                        state.memory.types.evalIsSubtype(array, receiverType),
                        excludesMissingSlot,
                    )
                }
            }
        }
    }

    private val pushDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        val (array, arrayType) = state.concreteArray(call, inputs)
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        val arguments = call.arguments.map { argument ->
            argument.resolved ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }
        if (!state.argumentsMatchArrayType(arrayType, arguments)) {
            return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }

        state.boundedArrayGuard(array, arrayType, maximumLength = MAX_SOURCE_ARRAY_LENGTH - arguments.size)
    }

    private val fillDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        val (array, arrayType) = state.concreteArray(call, inputs)
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        val value = call.arguments.firstOrNull()?.resolved
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        val valueMatchesArrayType = state.argumentsMatchArrayType(arrayType, listOf(value))
        val isDenseInput = state.isUnmodifiedDenseInputArray(array, arrayType)
        if (!valueMatchesArrayType || !isDenseInput) {
            return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }

        state.boundedArrayGuard(array, arrayType, maximumLength = MAX_SOURCE_ARRAY_LENGTH)
    }

    private val denseReceiverDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        val (array, arrayType) = state.concreteArray(call, inputs)
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        if (!state.isUnmodifiedDenseInputArray(array, arrayType)) {
            return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }

        state.boundedArrayGuard(array, arrayType, maximumLength = MAX_SOURCE_ARRAY_LENGTH)
    }

    private val unshiftDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        val (array, arrayType) = state.concreteArray(call, inputs)
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        val arguments = call.arguments.map { argument ->
            argument.resolved ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }
        if (
            !state.argumentsMatchArrayType(arrayType, arguments) ||
            !state.isUnmodifiedDenseInputArray(array, arrayType)
        ) {
            return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }

        state.boundedArrayGuard(array, arrayType, maximumLength = MAX_SOURCE_ARRAY_LENGTH - arguments.size)
    }

    private val concatDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        val (array, arrayType) = state.concreteArray(call, inputs)
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        val other = inputs.getOrNull(1) as? UConcreteHeapRef
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        val otherStaticType = call.arguments.singleOrNull()?.source?.type
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        val otherType = state.arrayStorageType(other, otherStaticType) as? EtsArrayType
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        if (
            arrayType != otherType ||
            !state.isUnmodifiedDenseInputArray(array, arrayType) ||
            !state.isUnmodifiedDenseInputArray(other, otherType)
        ) {
            return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }

        with(state.ctx) {
            val firstLength = state.memory.read(mkArrayLengthLValue(array, arrayType))
            val secondLength = state.memory.read(mkArrayLengthLValue(other, otherType))
            val firstIsBounded = state.boundedArrayGuard(
                array,
                arrayType,
                maximumLength = MAX_SOURCE_ARRAY_LENGTH,
            )
            val secondIsBounded = state.boundedArrayGuard(
                other,
                otherType,
                maximumLength = MAX_SOURCE_ARRAY_LENGTH,
            )
            val resultLength = mkBvAddExpr(firstLength, secondLength)
            val maximumResultLength = mkBv(MAX_SOURCE_ARRAY_LENGTH)
            val resultFits = mkBvSignedLessOrEqualExpr(resultLength, maximumResultLength)
            mkAnd(
                firstIsBounded,
                secondIsBounded,
                resultFits,
            )
        }
    }

    private val optionalFromIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 -> inputs + state.ctx.mkFp64(0.0)
                call.arguments.size == 2 && inputs.last() == state.ctx.mkUndefinedValue() -> {
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)
                }

                call.arguments.size == 2 && inputs.last().sort == state.ctx.fp64Sort -> inputs
                else -> null
            }
        }
    }

    private val optionalLastIndexAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        call.resolvedInstanceInputs()?.let { inputs ->
            when {
                call.arguments.size == 1 -> inputs + state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
                call.arguments.size == 2 && inputs.last() == state.ctx.mkUndefinedValue() -> {
                    inputs.dropLast(1) + state.ctx.mkFp64(0.0)
                }

                call.arguments.size == 2 && inputs.last().sort == state.ctx.fp64Sort -> inputs
                else -> null
            }
        }
    }

    private val variadicMutationAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs() ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val arguments = inputs.drop(1)
        if (arguments.size > MAX_VARIADIC_ARGUMENTS) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        buildList {
            add(inputs.first())
            addAll(arguments)
            repeat(MAX_VARIADIC_ARGUMENTS - arguments.size) {
                add(state.ctx.mkUndefinedValue())
            }
            add(state.ctx.mkFp64(arguments.size.toDouble()))
        }
    }

    private val fillAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs() ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val arguments = inputs.drop(1)
        if (arguments.isEmpty() || arguments.size > MAX_FILL_ARGUMENTS) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }
        if (
            arguments.drop(1).any { value ->
                value != state.ctx.mkUndefinedValue() && value.sort != state.ctx.fp64Sort
            }
        ) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        val start = arguments.getOrNull(1)
            ?.takeUnless { it == state.ctx.mkUndefinedValue() }
            ?: state.ctx.mkFp64(0.0)
        val end = arguments.getOrNull(2)
            ?.takeUnless { it == state.ctx.mkUndefinedValue() }
            ?: state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
        listOf(inputs.first(), arguments.first(), start, end)
    }

    private val sliceAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs() ?: return@TsEtsIrUnknownCallModelInputAdapter null
        val arguments = inputs.drop(1)
        if (arguments.size > 2 || arguments.any { value ->
                value != state.ctx.mkUndefinedValue() && value.sort != state.ctx.fp64Sort
            }
        ) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }

        val start = arguments.getOrNull(0)
            ?.takeUnless { it == state.ctx.mkUndefinedValue() }
            ?: state.ctx.mkFp64(0.0)
        val end = arguments.getOrNull(1)
            ?.takeUnless { it == state.ctx.mkUndefinedValue() }
            ?: state.ctx.mkFpInf(signBit = false, state.ctx.fp64Sort)
        listOf(inputs.first(), start, end)
    }

    private val noArgumentsAdapter = TsEtsIrUnknownCallModelInputAdapter { _, call ->
        if (call.arguments.isEmpty()) call.resolvedInstanceInputs() else null
    }

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(
            sourceModel(
                id = "ts.array.pop",
                methodName = "pop",
            ),
            sourceModel(
                id = "ts.array.indexOf",
                methodName = "indexOf",
                inputAdapter = optionalFromIndexAdapter,
            ),
            sourceModel(
                id = "ts.array.includes",
                methodName = "includes",
                inputAdapter = optionalFromIndexAdapter,
            ),
            sourceModel(
                id = "ts.array.lastIndexOf",
                methodName = "lastIndexOf",
                inputAdapter = optionalLastIndexAdapter,
            ),
            sourceModel(
                id = "ts.array.push",
                methodName = "push",
                inputAdapter = variadicMutationAdapter,
                domainGuard = pushDomain,
            ),
            sourceModel(
                id = "ts.array.fill",
                methodName = "fill",
                inputAdapter = fillAdapter,
                domainGuard = fillDomain,
            ),
            sourceModel(
                id = "ts.array.reverse",
                methodName = "reverse",
                inputAdapter = noArgumentsAdapter,
                domainGuard = denseReceiverDomain,
            ),
            sourceModel(
                id = "ts.array.unshift",
                methodName = "unshift",
                inputAdapter = variadicMutationAdapter,
                domainGuard = unshiftDomain,
            ),
            sourceModel(
                id = "ts.array.slice",
                methodName = "slice",
                inputAdapter = sliceAdapter,
                domainGuard = denseReceiverDomain,
            ),
            sourceModel(
                id = "ts.array.concat",
                methodName = "concat",
                domainGuard = concatDomain,
            ),
            primitiveModel(
                methodName = "grow",
                arity = 2,
                implementation = ::growArray,
            ),
            primitiveModel(
                methodName = "allocateLike",
                arity = 2,
                implementation = ::allocateArrayLike,
            ),
        )
    }

    private fun sourceModel(
        id: String,
        methodName: String,
        inputAdapter: TsEtsIrUnknownCallModelInputAdapter = TsEtsIrUnknownCallModelInputAdapter.IDENTITY,
        domainGuard: TsEtsIrUnknownCallModelDomainGuard = arrayDomain,
    ): TsUnknownCallModel {
        val target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = "Array".takeUnless { methodName == "pop" },
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        )

        return TsEtsIrUnknownCallModel(
            id = id,
            target = target,
            artifact = artifact(methodName),
            domainGuard = domainGuard,
            inputAdapter = inputAdapter,
            requiredModelIds = buildSet {
                if (methodName in setOf("indexOf", "includes", "lastIndexOf", "fill", "reverse", "slice")) {
                    add(MATH_FLOOR_MODEL_ID)
                }
                if (methodName in setOf("push", "unshift")) {
                    add(PRIMITIVE_GROW_ID)
                }
                if (methodName in setOf("slice", "concat")) {
                    add(PRIMITIVE_ALLOCATE_LIKE_ID)
                }
            },
        )
    }

    private fun artifact(methodName: String): TsEtsIrUnknownCallModelArtifact {
        val artifact = baseArtifact
        val entryPoint = artifact.file.allClasses
            .single { it.name == CLASS_NAME }
            .methods
            .single { it.name == methodName }

        return artifact.copy(entryPoint = entryPoint)
    }

    private fun TsState.concreteArray(
        call: TsUnknownCall,
        inputs: List<UExpr<*>>,
    ): Pair<UConcreteHeapRef, EtsArrayType>? {
        val receiver = inputs.firstOrNull() as? UConcreteHeapRef ?: return null
        if (with(ctx) { receiver.hasFakeValueBranch() }) return null

        val staticType = call.receiver?.source?.type ?: return null
        val arrayType = arrayStorageType(receiver, staticType) as? EtsArrayType ?: return null
        if (arrayType.dimensions != 1) return null

        return receiver to arrayType
    }

    private fun TsState.argumentsMatchArrayType(
        arrayType: EtsArrayType,
        arguments: List<UExpr<*>>,
    ): Boolean = with(ctx) {
        val elementSort = typeToSort(arrayType.elementType)
        elementSort is TsUnresolvedSort || arguments.all { argument ->
            argument.sort == elementSort &&
                (argument.sort != addressSort || !argument.asExpr(addressSort).hasFakeValueBranch())
        }
    }

    private fun TsState.boundedArrayGuard(
        array: UConcreteHeapRef,
        arrayType: EtsArrayType,
        maximumLength: Int,
    ): UBoolExpr = with(ctx) {
        val length = memory.read(mkArrayLengthLValue(array, arrayType))
        val minimumLength = mkBv(0)
        val maximumLengthExpr = mkBv(maximumLength)
        mkAnd(
            memory.types.evalIsSubtype(array, arrayType),
            mkBvSignedGreaterOrEqualExpr(length, minimumLength),
            mkBvSignedLessOrEqualExpr(length, maximumLengthExpr),
        )
    }

    private fun primitiveModel(
        methodName: String,
        arity: Int,
        implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
    ): TsUnknownCallModel = ArrayPrimitiveModel(
        methodName = methodName,
        arity = arity,
        implementation = implementation,
    )

    private fun growArray(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val receiver = inputs.getOrNull(0) as? UConcreteHeapRef ?: return null
        val fpLength = inputs.getOrNull(1)?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        val arrayType = state.arrayStorageType(
            receiver,
            EtsArrayType(EtsUnknownType, dimensions = 1),
        ) as? EtsArrayType ?: return null
        val length = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = fpLength,
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)
        val roundTrip = mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = length.cast(),
            signed = true,
        )
        val receiverMatchesType = state.memory.types.evalIsSubtype(receiver, arrayType)
        val minimumLength = mkBv(0)
        val maximumLength = mkBv(MAX_SOURCE_ARRAY_LENGTH)
        val guard = mkAnd(
            receiverMatchesType,
            mkFpEqualExpr(roundTrip, fpLength),
            mkBvSignedGreaterOrEqualExpr(length, minimumLength),
            mkBvSignedLessOrEqualExpr(length, maximumLength),
        )
        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.Normal {
                state.memory.write(
                    mkArrayLengthLValue(receiver, arrayType),
                    length,
                    guard = trueExpr,
                )
                mkUndefinedValue()
            },
        )

        TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = guard.takeUnless { it == trueExpr }?.let(::mkNot),
        )
    }

    private fun allocateArrayLike(
        state: TsState,
        inputs: List<UExpr<*>>,
    ): TsUnknownCallModelExecution? = with(state.ctx) {
        val receiver = inputs.getOrNull(0) as? UConcreteHeapRef ?: return null
        val fpLength = inputs.getOrNull(1)?.takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        val arrayType = state.arrayStorageType(
            receiver,
            EtsArrayType(EtsUnknownType, dimensions = 1),
        ) as? EtsArrayType ?: return null
        val length = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = fpLength,
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)
        val receiverMatchesType = state.memory.types.evalIsSubtype(receiver, arrayType)
        val minimumLength = mkBv(0)
        val maximumLength = mkBv(MAX_SOURCE_ARRAY_LENGTH)
        val guard = mkAnd(
            receiverMatchesType,
            mkBvSignedGreaterOrEqualExpr(length, minimumLength),
            mkBvSignedLessOrEqualExpr(length, maximumLength),
        )
        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.Normal {
                val descriptor = arrayDescriptorOf(arrayType)
                val result = state.memory.allocConcrete(descriptor)
                state.memory.initializeArrayLength(
                    arrayHeapRef = result,
                    type = descriptor,
                    sizeSort = sizeSort,
                    count = length,
                )
                result
            },
        )

        TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = guard.takeUnless { it == trueExpr }?.let(::mkNot),
        )
    }

    private fun TsUnknownCall.resolvedInstanceInputs(): List<UExpr<*>>? {
        val resolvedReceiver = receiver?.resolved ?: return null
        val resolvedArguments = arguments.map { argument -> argument.resolved ?: return null }

        return listOf(resolvedReceiver) + resolvedArguments
    }

    private class ArrayPrimitiveModel(
        methodName: String,
        private val arity: Int,
        private val implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
    ) : TsUnknownCallModel {
        override val id: String = "ts.array.primitive.$methodName"
        override val target = TsUnknownCallTarget(
            methodName = methodName,
            enclosingClassName = PRIMITIVES_CLASS_NAME,
            failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
        )

        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
            if (call.receiver != null || call.arguments.size != arity) {
                return null
            }

            val inputs = call.arguments.map { argument -> argument.resolved ?: return null }
            return implementation(state, inputs)
        }
    }

    private const val MATH_FLOOR_MODEL_ID = "ts.math.floor"
    private const val PRIMITIVE_GROW_ID = "ts.array.primitive.grow"
    private const val PRIMITIVE_ALLOCATE_LIKE_ID = "ts.array.primitive.allocateLike"
}
