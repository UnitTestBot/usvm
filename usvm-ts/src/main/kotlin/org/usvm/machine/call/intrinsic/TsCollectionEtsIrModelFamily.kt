package org.usvm.machine.call.intrinsic

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.typeStreamOf
import org.usvm.machine.call.TsEtsIrUnknownCallModel
import org.usvm.machine.call.TsEtsIrUnknownCallModelArtifact
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.call.hasBuiltinGlobalOwner
import org.usvm.machine.call.loadBundledEtsIrUnknownCallModelArtifact
import org.usvm.machine.expr.TsCollectionKind
import org.usvm.machine.expr.newModeledCollection
import org.usvm.machine.state.TsState
import org.usvm.types.singleOrNull
import org.usvm.util.arrayStorageType

/** Iterable construction executes normal source bodies over modeled native storage. */
internal object TsCollectionEtsIrModelFamily : TsBuiltInUnknownCallModelFamily {
    private const val CLASS_NAME = "CollectionModels"
    private const val PRIMITIVES_NAME = "CollectionModelPrimitives"
    private val baseArtifact by lazy {
        loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = "/org/usvm/machine/call/models/CollectionModels.ts",
            sourceFileName = "CollectionModels.ts",
            entryPointClassName = CLASS_NAME,
            entryPointMethodName = "arrayFrom",
        )
    }
    private val sourceModelIds by lazy { models.filterIsInstance<TsEtsIrUnknownCallModel>().map { it.id }.toSet() }

    override val models: List<TsUnknownCallModel> by lazy {
        listOf(
            constructor(kind = TsCollectionKind.MAP, name = "Map"),
            constructor(kind = TsCollectionKind.SET, name = "Set"),
            populate(kind = TsCollectionKind.MAP, name = "Map"),
            populate(kind = TsCollectionKind.SET, name = "Set"),
            initializer(kind = TsCollectionKind.MAP, name = "initializeMap"),
            initializer(kind = TsCollectionKind.SET, name = "initializeSet"),
            arrayFromModel(),
        )
    }

    private fun arrayFromModel(): TsUnknownCallModel = TsEtsIrUnknownCallModel(
        id = "ts.array.fromIterable",
        target = TsUnknownCallTarget(methodName = "from"),
        artifact = artifact("arrayFrom"),
        inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
            val owner = call.receiver?.source ?: return@TsEtsIrUnknownCallModelInputAdapter null
            if (!hasBuiltinGlobalOwner(owner, call.callee, expectedName = "Array")) {
                return@TsEtsIrUnknownCallModelInputAdapter null
            }
            val arguments = call.arguments.map {
                it.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null
            }
            val input = arguments.firstOrNull() ?: return@TsEtsIrUnknownCallModelInputAdapter null
            val mapping = arguments.getOrNull(1) ?: state.ctx.mkUndefinedValue()
            val supportedInput = state.supportsIterable(input, call.arguments[0].source.type)
            if (mapping != state.ctx.mkUndefinedValue() || !supportedInput) {
                return@TsEtsIrUnknownCallModelInputAdapter null
            }
            listOf(input)
        },
        requiredModelIds = setOf(
            "ts.array.push",
            "ts.array.primitive.grow",
            TsIteratorModelFamily.COLLECTION_ITERATOR_ID,
        ),
    )


    private fun constructor(kind: TsCollectionKind, name: String): TsUnknownCallModel {
        val entry = if (kind == TsCollectionKind.MAP) "mapConstructor" else "setConstructor"
        val initialize = if (kind == TsCollectionKind.MAP) "initializeMap" else "initializeSet"
        val signature = EtsClassSignature.UNKNOWN.copy(name = name)
        return TsEtsIrUnknownCallModel(
            id = "ts.${name.lowercase()}.constructor",
            target = TsUnknownCallTarget(methodName = CONSTRUCTOR_NAME, enclosingClassName = name),
            artifact = artifact(entry),
            inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
                val receiver = call.receiver?.resolved as? UConcreteHeapRef
                    ?: return@TsEtsIrUnknownCallModelInputAdapter null
                val runtimeType = state.memory.typeStreamOf(receiver).singleOrNull()
                if (call.callee.enclosingClass != signature || runtimeType != EtsClassType(signature)) {
                    return@TsEtsIrUnknownCallModelInputAdapter null
                }
                val arguments = call.arguments.map { it.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null }
                val input = arguments.firstOrNull() ?: state.ctx.mkUndefinedValue()
                listOf(receiver, input)
            },
            requiredModelIds = setOf("ts.collection.primitive.$initialize"),
        )
    }

    // Optional populate helpers keep the original constructor model usable without iterable models.
    private fun populate(kind: TsCollectionKind, name: String): TsUnknownCallModel {
        val entry = if (kind == TsCollectionKind.MAP) "mapFromIterable" else "setFromIterable"
        val helper = if (kind == TsCollectionKind.MAP) "populateMap" else "populateSet"
        val initialize = if (kind == TsCollectionKind.MAP) "initializeMap" else "initializeSet"
        val operation = if (kind == TsCollectionKind.MAP) "set" else "add"
        return TsEtsIrUnknownCallModel(
            id = "ts.${name.lowercase()}.fromIterable",
            target = TsUnknownCallTarget(
                methodName = helper,
                enclosingClassName = PRIMITIVES_NAME,
                failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
            ),
            artifact = artifact(entry),
            inputAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
                val sourceFile = baseArtifact.file
                val helperSignature = sourceFile.allClasses.single { it.name == PRIMITIVES_NAME }.signature
                val caller = call.callSite.location.method
                val sourceCaller = caller.signature.enclosingClass
                val ownedCall = call.callee.enclosingClass == helperSignature &&
                    sourceCaller == baseArtifact.entryPoint.signature.enclosingClass &&
                    state.isUnknownCallModelActive("ts.${name.lowercase()}.constructor")
                if (!ownedCall) return@TsEtsIrUnknownCallModelInputAdapter null
                val inputs = call.arguments.map { it.resolved ?: return@TsEtsIrUnknownCallModelInputAdapter null }
                if (inputs.size != 2) return@TsEtsIrUnknownCallModelInputAdapter null
                val supportedInput = state.supportsIterable(inputs[1], call.arguments[1].source.type)
                if (!supportedInput) {
                    return@TsEtsIrUnknownCallModelInputAdapter null
                }
                inputs
            },
            requiredModelIds = setOf(
                "ts.collection.primitive.$initialize",
                "ts.collection.$operation",
                "ts.typeError.constructor",
                TsIteratorModelFamily.COLLECTION_ITERATOR_ID,
            ),
        )
    }

    private fun initializer(kind: TsCollectionKind, name: String): TsUnknownCallModel = TsPrimitiveUnknownCallModel(
        idPrefix = "ts.collection.primitive",
        methodName = name,
        enclosingClassName = PRIMITIVES_NAME,
        arity = 1,
        helperClassSignature = { baseArtifact.file.allClasses.single { it.name == PRIMITIVES_NAME }.signature },
        sourceClassSignature = { baseArtifact.entryPoint.signature.enclosingClass },
        owningModelIds = { sourceModelIds },
        implementation = { state, inputs ->
            val receiver = inputs.single() as? UConcreteHeapRef
            if (receiver == null) {
                null
            } else {
                val successor = TsUnknownCallModelSuccessor(
                    guard = state.ctx.trueExpr,
                    completion = TsUnknownCallModelCompletion.Normal {
                        modeledCollections += receiver to newModeledCollection(kind)
                        ctx.mkUndefinedValue()
                    },
                )
                TsUnknownCallModelExecution(successors = listOf(successor))
            }
        },
        unsupportedCallPolicy = TsResidualCallPolicy.STOP_PATH,
    )

    private fun TsState.supportsIterable(input: UExpr<*>, staticType: EtsType): Boolean {
        val reference = input as? UConcreteHeapRef ?: return false
        val knownNative = reference in modeledCollections || reference in collectionIterators ||
            reference in arrayIterators || reference in stringIterators
        if (knownNative) return true
        val type = arrayStorageType(reference, staticType)
        return type is EtsArrayType || type == EtsStringType
    }

    private fun artifact(name: String): TsEtsIrUnknownCallModelArtifact {
        val artifact = baseArtifact
        val method = artifact.file.allClasses
            .single { it.name == CLASS_NAME }
            .methods
            .single { it.name == name }
        return artifact.copy(entryPoint = method)
    }
}
