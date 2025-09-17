package org.usvm.machine.state

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.TsTarget
import org.usvm.api.allocateConcreteRef
import org.usvm.api.initializeArray
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.TsFunction
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.util.mkFieldLValue
import org.usvm.util.type

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
    val addedArtificialLocals: MutableSet<String> = hashSetOf(),
    val lValuesToAllocatedFakeObjects: MutableList<Pair<ULValue<*, *>, UConcreteHeapRef>> = mutableListOf(),
    var discoveredCallees: UPersistentHashMap<Pair<EtsStmt, Int>, EtsBlockCfg> = persistentHashMapOf(),
    var promiseState: UPersistentHashMap<UConcreteHeapRef, PromiseState> = persistentHashMapOf(),
    var promiseExecutor: UPersistentHashMap<UConcreteHeapRef, EtsMethod> = persistentHashMapOf(),
    var methodToRef: UPersistentHashMap<EtsMethod, UConcreteHeapRef> = persistentHashMapOf(),
    var associatedFunction: UPersistentHashMap<UConcreteHeapRef, TsFunction> = persistentHashMapOf(),
    var closureObject: UPersistentHashMap<String, UConcreteHeapRef> = persistentHashMapOf(),
    var boundThis: UPersistentHashMap<UConcreteHeapRef, UHeapRef> = persistentHashMapOf(),
    var dfltObject: UPersistentHashMap<EtsFileSignature, UConcreteHeapRef> = persistentHashMapOf(),

    /**
     * Maps (file signature, field name) to the sort used for that field in the dflt object.
     * This tracks sorts for global variables that are represented as fields of dflt objects.
     */
    var dfltObjectFieldSorts: UPersistentHashMap<Pair<EtsFileSignature, String>, USort> = persistentHashMapOf(),

    /**
     * Maps string values to their corresponding heap references that were allocated for string constants.
     * This tracks which string constants have been initialized in this particular state to avoid
     * duplicate initialization while ensuring all states use the same heap reference from the context
     * for identical string values.
     */
    var stringConstantAllocatedRefs: UPersistentHashMap<String, UConcreteHeapRef> = persistentHashMapOf(),
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

    fun pushLocalToSortStack() {
        localToSortStack.add(persistentHashMapOf())
    }

    fun popLocalToSortStack() {
        localToSortStack.removeLast()
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

    fun getMethodRef(
        method: EtsMethod,
        thisInstance: UHeapRef? = null,
    ): UConcreteHeapRef {
        val (updated, result) = methodToRef.getOrPut(method, ownership) { ctx.allocateConcreteRef() }
        associatedFunction = associatedFunction.put(result, TsFunction(method, thisInstance), ownership)
        methodToRef = updated
        return result
    }

    fun setClosureObject(
        name: String,
        closure: UConcreteHeapRef,
    ) {
        closureObject = closureObject.put(name, closure, ownership)
    }

    fun setBoundThis(
        instance: UConcreteHeapRef,
        thisRef: UHeapRef,
    ) {
        boundThis = boundThis.put(instance, thisRef, ownership)
    }

    fun getDfltObject(file: EtsFile): UConcreteHeapRef {
        val (updated, result) = dfltObject.getOrPut(file.signature, ownership) {
            // val dfltClass = file.getDfltClass()
            // memory.allocConcrete(dfltClass.type)
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
     * Initializes and returns a fully constructed string constant in this state's memory.
     * This function handles both heap reference allocation (via context) and memory initialization.
     * Use this when you need a complete, usable string object in the current state.
     */
    fun mkInitializedStringConstant(
        value: String,
    ): UConcreteHeapRef = with(ctx) {
        // Get the shared reference from context
        val ref = mkStringConstantRef(value)

        // Check if we've already initialized this string constant in our state
        val (updated, result) = stringConstantAllocatedRefs.getOrPut(value, ownership) {
            // Initialize the string constant only if not already done in this state
            // Allocate type information for this ref in this state's memory
            memory.types.allocate(ref.address, EtsStringType)

            // Initialize char array
            val valueType = EtsArrayType(EtsNumberType, dimensions = 1)
            val descriptor = ctx.arrayDescriptorOf(valueType)

            val charArray = memory.allocConcrete(valueType.elementType)
            memory.initializeArray(
                arrayHeapRef = charArray,
                type = descriptor,
                sort = bv16Sort,
                sizeSort = sizeSort,
                contents = value.asSequence().map { mkBv(it.code, bv16Sort) },
            )

            // Write char array to `ref.value`
            val valueLValue = mkFieldLValue(addressSort, ref, "value")
            memory.write(valueLValue, charArray, guard = trueExpr)

            ref
        }
        stringConstantAllocatedRefs = updated
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
            addedArtificialLocals = addedArtificialLocals,
            lValuesToAllocatedFakeObjects = lValuesToAllocatedFakeObjects.toMutableList(),
            discoveredCallees = discoveredCallees,
            promiseState = promiseState,
            promiseExecutor = promiseExecutor,
            methodToRef = methodToRef,
            associatedFunction = associatedFunction,
            closureObject = closureObject,
            boundThis = boundThis,
            dfltObject = dfltObject,
            dfltObjectFieldSorts = dfltObjectFieldSorts,
            stringConstantAllocatedRefs = stringConstantAllocatedRefs,
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is TsMethodResult.TsException
}
