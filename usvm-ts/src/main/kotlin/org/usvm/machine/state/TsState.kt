package org.usvm.machine.state

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.PathNode
import org.usvm.UBoolExpr
import org.usvm.UCallStack
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.allocateConcreteRef
import org.usvm.api.initializeArray
import org.usvm.api.targets.TsTarget
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.TsContext
import org.usvm.machine.TsInputPropertyPresence
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.TrackedObjectProperty
import org.usvm.machine.expr.TsArrayIterator
import org.usvm.machine.expr.TsCapturedInstanceCall
import org.usvm.machine.expr.TsIteratorResult
import org.usvm.machine.expr.TsStringIterator
import org.usvm.machine.interpreter.PromiseCallback
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.TsFunction
import org.usvm.machine.runtimeFunctionType
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.type

/** Observable completion of an auxiliary entry-point guard. */
enum class TsEntryPointGuardOutcome {
    NONE,
    REJECTED,
    ERROR,
}

/**
 * [lValuesToAllocatedFakeObjects] contains records of l-values that were allocated with newly created fake objects.
 * It is important for result interpreters to be able to restore the order of fake objects allocation and
 * their assignment to evaluated l-values.
 *
 * [discoveredCallees] is a map from each [EtsStmt] with EtsCallExpr inside and its parent block id to its
 * analyzed callee CFG. This grows dynamically as you step through the calls.
 */
class TsState(
    ctx: TsContext,
    ownership: MutabilityOwnership,
    override val entrypoint: EtsMethod,
    val maxStringLength: Int,
    val inputPropertyPresence: TsInputPropertyPresence = TsInputPropertyPresence.DECLARED_FIELDS,
    callStack: UCallStack<EtsMethod, EtsStmt> = UCallStack(),
    pathConstraints: UPathConstraints<EtsType> = UPathConstraints(ctx, ownership),
    memory: UMemory<EtsType, EtsMethod> = UMemory(ctx, ownership, pathConstraints.typeConstraints),
    models: List<UModelBase<EtsType>> = listOf(),
    pathNode: PathNode<EtsStmt> = PathNode.root(),
    forkPoints: PathNode<PathNode<EtsStmt>> = PathNode.root(),
    var methodResult: TsMethodResult = TsMethodResult.NoCall,
    targets: UTargetsSet<TsTarget, EtsStmt> = UTargetsSet.empty(),
    val localToSortStack: MutableList<UPersistentHashMap<Int, USort>> = mutableListOf(persistentHashMapOf()),
    var staticStorage: UPersistentHashMap<EtsClass, UConcreteHeapRef> = persistentHashMapOf(),
    internal var staticInitializationFailures: Map<EtsClass, TsMethodResult.TsException> = emptyMap(),
    val addedArtificialLocals: MutableSet<String> = hashSetOf(),
    val lValuesToAllocatedFakeObjects: MutableList<Pair<ULValue<*, *>, UConcreteHeapRef>> = mutableListOf(),
    var discoveredCallees: UPersistentHashMap<Pair<EtsStmt, Int>, EtsBlockCfg> = persistentHashMapOf(),
    var promiseState: UPersistentHashMap<UConcreteHeapRef, PromiseState> = persistentHashMapOf(),
    var promiseExecutor: UPersistentHashMap<UConcreteHeapRef, EtsMethod> = persistentHashMapOf(),
    internal var promiseCallbacks: UPersistentHashMap<UConcreteHeapRef, PromiseCallback> = persistentHashMapOf(),
    var methodToRef: UPersistentHashMap<EtsMethod, UConcreteHeapRef> = persistentHashMapOf(),
    var associatedFunction: UPersistentHashMap<UConcreteHeapRef, TsFunction> = persistentHashMapOf(),
    var capturedInstanceCalls: UPersistentHashMap<UConcreteHeapRef, TsCapturedInstanceCall> = persistentHashMapOf(),
    internal var arrayIterators: Map<UConcreteHeapRef, TsArrayIterator> = emptyMap(),
    internal var stringIterators: Map<UConcreteHeapRef, TsStringIterator> = emptyMap(),
    /** Intrinsic literal allocations; null until their constant constructor arguments are initialized. */
    internal var regularExpressions: Map<UConcreteHeapRef, String?> = emptyMap(),
    internal var iteratorResults: Map<UConcreteHeapRef, TsIteratorResult> = emptyMap(),
    internal var knownArrayPropertyPresence: Set<UHeapRef> = emptySet(),
    internal var unsupportedIteratorArrays: Set<UHeapRef> = emptySet(),
    internal var initializedArrayIndices: Map<UHeapRef, Map<UExpr<TsSizeSort>, UBoolExpr>> = emptyMap(),
    var boundThis: UPersistentHashMap<UConcreteHeapRef, UHeapRef> = persistentHashMapOf(),
    var dfltObject: UPersistentHashMap<EtsFileSignature, UConcreteHeapRef> = persistentHashMapOf(),

    /**
     * Maps (file signature, field name) to the sort used for that field in the dflt object.
     * This tracks sorts for global variables that are represented as fields of dflt objects.
     */
    var dfltObjectFieldSorts: UPersistentHashMap<Pair<EtsFileSignature, String>, USort> = persistentHashMapOf(),

    /** True while an auxiliary entry-point guard or one of its callees is executing. */
    var entryPointGuardActive: Boolean = false,

    /** Terminal guard outcome; [TsEntryPointGuardOutcome.NONE] also covers predicate execution. */
    var entryPointGuardOutcome: TsEntryPointGuardOutcome = TsEntryPointGuardOutcome.NONE,

    /**
     * Maps string values to their corresponding heap references that were allocated for string constants.
     * This tracks which string constants have been initialized in this particular state to avoid
     * duplicate initialization while ensuring all states use the same heap reference from the context
     * for identical string values.
     */
    var stringConstantAllocatedRefs: UPersistentHashMap<String, UConcreteHeapRef> = persistentHashMapOf(),
    /**
     * Maps references with modeled string backing to their maximum length.
     * A new symbolic string producer must register its reference here after creating the backing model.
     * String literals are recognized separately through [TsContext.getStringConstantValue].
     */
    var boundedStringBackingRefs: Map<UHeapRef, Int> = emptyMap(),
    /** Unresolved reference payloads that may acquire string backing after type refinement. */
    var symbolicStringCandidates: Set<UHeapRef> = emptySet(),
    /** Exception delivered to the current catch block, before its binding is initialized. */
    var caughtException: UExpr<*>? = null,
    var unsupportedReason: String? = null,
    /** Diagnostic prefix of a failed interpreter step, never a completed program result. */
    var engineFailureReason: String? = null,
    var trackedObjectProperties: Set<TrackedObjectProperty> = emptySet(),
    var writtenConcreteFields: Set<Pair<UHeapRef, String>> = emptySet(),
    var initializedTypedArrayOverrides: Set<UHeapRef> = emptySet(),
    var writtenObjectLiteralFieldSorts: UPersistentHashMap<Pair<UHeapRef, String>, USort> = persistentHashMapOf(),
    internal val denseInputArrays: MutableMap<UConcreteHeapRef, TsDenseInputArray> = mutableMapOf(),
    private val activeUnknownCallModels: MutableList<Pair<String, Int>> = mutableListOf(),
) : UState<EtsType, EtsMethod, EtsStmt, TsContext, TsTarget, TsState>(
    ctx = ctx,
    initOwnership = ownership,
    callStack = callStack,
    pathConstraints = pathConstraints,
    memory = memory,
    models = models,
    pathNode = pathNode,
    forkPoints = forkPoints,
    targets = targets,
) {
    /** Terminates a satisfiable path that the TypeScript model cannot execute soundly. */
    fun terminateAsUnsupported(reason: String) {
        require(reason.isNotBlank())

        unsupportedReason = reason
        while (callStack.isNotEmpty()) callStack.pop()
    }

    fun getSortForLocal(idx: Int): USort? {
        val localToSort = localToSortStack.last()
        return localToSort[idx]
    }

    fun getOrPutSortForLocal(idx: Int, sort: () -> USort): USort {
        val localToSort = localToSortStack.last()
        val (updated, result) = localToSort.getOrPut(idx, ownership, sort)
        localToSortStack[localToSortStack.lastIndex] = updated
        return result
    }

    fun saveSortForLocal(idx: Int, sort: USort) {
        val localToSort = localToSortStack.last()
        val updated = localToSort.put(idx, sort, ownership)
        localToSortStack[localToSortStack.lastIndex] = updated
    }

    fun saveObjectLiteralFieldSort(ref: UHeapRef, fieldName: String, sort: USort) {
        writtenObjectLiteralFieldSorts = writtenObjectLiteralFieldSorts.put(ref to fieldName, sort, ownership)
    }

    fun pushLocalToSortStack() {
        localToSortStack.add(persistentHashMapOf())
    }

    fun popLocalToSortStack() {
        localToSortStack.removeLast()
    }

    fun isUnknownCallModelActive(modelId: String): Boolean =
        activeUnknownCallModels.any { (activeModelId, _) -> activeModelId == modelId }

    /** A source helper may execute only under the dispatcher-owned frame from the same artifact. */
    internal fun isUnknownCallModelFileActive(owner: EtsClassSignature): Boolean {
        val file = owner.file
        return activeUnknownCallModels.any { (_, depth) ->
            callStack[depth - 1].method.signature.enclosingClass.file == file
        }
    }

    fun enterUnknownCallModel(modelId: String) {
        val entryCallDepth = callStack.size + 1
        activeUnknownCallModels += modelId to entryCallDepth
    }

    fun leaveUnknownCallModelIfReturning() {
        val activeModel = activeUnknownCallModels.lastOrNull()
        if (activeModel?.second == callStack.size) {
            activeUnknownCallModels.removeLast()
        }
    }

    fun registerCallee(stmt: EtsStmt, cfg: EtsBlockCfg) {
        val parentId = stmt.location.method.cfg.blocks.indexOfFirst { it.statements.contains(stmt) }
            .takeIf { it >= 0 } ?: error("Statement $stmt is not found in the method CFG")

        val key = stmt to parentId
        if (key !in discoveredCallees) {
            discoveredCallees = discoveredCallees.put(key, cfg, ownership)
        }
    }

    fun pushSortsForArguments(
        n: Int,
        idxToSort: (Int) -> USort?,
    ) {
        pushLocalToSortStack()
        for (i in 0..n) {
            val sort = idxToSort(i)
            if (sort != null) {
                saveSortForLocal(i, sort)
            }
        }
    }

    fun pushSortsForActualArguments(
        arguments: List<UExpr<*>>,
    ) {
        pushLocalToSortStack()
        arguments.forEachIndexed { idx, arg ->
            saveSortForLocal(idx, arg.sort)
        }
    }

    fun getStaticInstance(clazz: EtsClass): UConcreteHeapRef {
        val (updated, result) = staticStorage.getOrPut(clazz, ownership) {
            memory.allocConcrete(clazz.type)
        }
        staticStorage = updated
        return result
    }

    fun setPromiseState(
        promise: UConcreteHeapRef,
        state: PromiseState,
    ) {
        promiseState = promiseState.put(promise, state, ownership)
    }

    fun setPromiseExecutor(
        promise: UConcreteHeapRef,
        method: EtsMethod,
    ) {
        promiseExecutor = promiseExecutor.put(promise, method, ownership)
    }

    internal fun registerPromiseCallback(ref: UConcreteHeapRef, callback: PromiseCallback) {
        promiseCallbacks = promiseCallbacks.put(ref, callback, ownership)
    }

    fun getMethodRef(
        method: EtsMethod,
        thisInstance: UHeapRef? = null,
        closure: UHeapRef? = null,
    ): UConcreteHeapRef {
        val result = if (closure == null) {
            val (updated, methodRef) = methodToRef.getOrPut(method, ownership) {
                memory.allocConcrete(ctx.scene.runtimeFunctionType())
            }
            methodToRef = updated
            methodRef
        } else {
            memory.allocConcrete(ctx.scene.runtimeFunctionType())
        }
        associatedFunction = associatedFunction.put(result, TsFunction(method, thisInstance, closure), ownership)
        return result
    }

    fun setBoundThis(
        instance: UConcreteHeapRef,
        thisRef: UHeapRef,
    ) {
        boundThis = boundThis.put(instance, thisRef, ownership)
    }

    fun getDfltObject(file: EtsFile): UConcreteHeapRef {
        val (updated, result) = dfltObject.getOrPut(file.signature, ownership) {
            ctx.allocateConcreteRef()
        }
        dfltObject = updated
        return result
    }

    fun getSortForDfltObjectField(
        file: EtsFile,
        fieldName: String,
    ): USort? {
        return dfltObjectFieldSorts[file.signature to fieldName]
    }

    fun saveSortForDfltObjectField(
        file: EtsFile,
        fieldName: String,
        sort: USort,
    ) {
        dfltObjectFieldSorts = dfltObjectFieldSorts.put(file.signature to fieldName, sort, ownership)
    }

    /**
     * Initializes the reference interned by the context and its backing once in this state's memory.
     */
    fun mkInitializedStringConstant(
        value: String,
    ): UConcreteHeapRef = with(ctx) {
        val ref = mkStringConstantRef(value)

        val (updated, result) = stringConstantAllocatedRefs.getOrPut(value, ownership) {
            memory.types.allocate(ref.address, EtsStringType)

            val charArray = memory.allocConcrete(EtsNumberType)
            memory.initializeArray(
                arrayHeapRef = charArray,
                type = stringBackingArrayDescriptor,
                sort = bv16Sort,
                sizeSort = sizeSort,
                contents = value.asSequence().map { mkBv(it.code, bv16Sort) },
            )

            val backingLValue = mkStringBackingLValue(ref)
            memory.write(backingLValue, charArray, guard = trueExpr)

            ref
        }
        stringConstantAllocatedRefs = updated
        boundedStringBackingRefs += result to value.length
        result
    }

    override fun clone(newConstraints: UPathConstraints<EtsType>?): TsState {
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val clonedConstraints = newConstraints?.also {
            this.pathConstraints.changeOwnership(newThisOwnership)
            it.changeOwnership(cloneOwnership)
        } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        this.ownership = newThisOwnership

        return TsState(
            ctx = ctx,
            ownership = cloneOwnership,
            entrypoint = entrypoint,
            maxStringLength = maxStringLength,
            inputPropertyPresence = inputPropertyPresence,
            callStack = callStack.clone(),
            pathConstraints = clonedConstraints,
            memory = memory.clone(clonedConstraints.typeConstraints, newThisOwnership, cloneOwnership),
            models = models,
            pathNode = pathNode,
            forkPoints = forkPoints,
            methodResult = methodResult,
            targets = targets.clone(),
            localToSortStack = localToSortStack.toMutableList(),
            staticStorage = staticStorage,
            staticInitializationFailures = staticInitializationFailures,
            addedArtificialLocals = addedArtificialLocals.toMutableSet(),
            lValuesToAllocatedFakeObjects = lValuesToAllocatedFakeObjects.toMutableList(),
            discoveredCallees = discoveredCallees,
            promiseState = promiseState,
            promiseExecutor = promiseExecutor,
            promiseCallbacks = promiseCallbacks,
            methodToRef = methodToRef,
            associatedFunction = associatedFunction,
            capturedInstanceCalls = capturedInstanceCalls,
            arrayIterators = arrayIterators,
            iteratorResults = iteratorResults,
            stringIterators = stringIterators,
            regularExpressions = regularExpressions,
            initializedArrayIndices = initializedArrayIndices,
            unsupportedIteratorArrays = unsupportedIteratorArrays,
            knownArrayPropertyPresence = knownArrayPropertyPresence,
            boundThis = boundThis,
            dfltObject = dfltObject,
            dfltObjectFieldSorts = dfltObjectFieldSorts,
            entryPointGuardActive = entryPointGuardActive,
            entryPointGuardOutcome = entryPointGuardOutcome,
            stringConstantAllocatedRefs = stringConstantAllocatedRefs,
            boundedStringBackingRefs = boundedStringBackingRefs,
            symbolicStringCandidates = symbolicStringCandidates,
            caughtException = caughtException,
            unsupportedReason = unsupportedReason,
            engineFailureReason = engineFailureReason,
            trackedObjectProperties = trackedObjectProperties,
            writtenConcreteFields = writtenConcreteFields,
            initializedTypedArrayOverrides = initializedTypedArrayOverrides,
            writtenObjectLiteralFieldSorts = writtenObjectLiteralFieldSorts,
            denseInputArrays = denseInputArrays.toMutableMap(),
            activeUnknownCallModels = activeUnknownCallModels.toMutableList(),
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is TsMethodResult.TsException
}

/** Snapshot of the regions that establish dense Calls input-array contents. */
data class TsDenseInputArray(
    val type: EtsArrayType,
    val length: UExpr<TsSizeSort>,
    val lengthRegion: UReadOnlyMemoryRegion<*, *>,
    val elementRegion: UReadOnlyMemoryRegion<*, *>,
)
