package org.usvm.machine.state

import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsValue
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.targets.TsTarget
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.PromiseState
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet
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
    val globalObject: UConcreteHeapRef = memory.allocStatic(EtsClassType(EtsClassSignature.UNKNOWN)),
    val addedArtificialLocals: MutableSet<String> = hashSetOf(),
    val lValuesToAllocatedFakeObjects: MutableList<Pair<ULValue<*, *>, UConcreteHeapRef>> = mutableListOf(),
    var discoveredCallees: UPersistentHashMap<Pair<EtsStmt, Int>, EtsBlockCfg> = persistentHashMapOf(),
    var promiseStates: UPersistentHashMap<UConcreteHeapRef, PromiseState> = persistentHashMapOf(),
    var promiseExecutors: UPersistentHashMap<UConcreteHeapRef, EtsMethod> = persistentHashMapOf(),
    // TODO: use normal naming
    var method2ref: UPersistentHashMap<EtsMethod, UConcreteHeapRef> = persistentHashMapOf(),
    var associatedMethods: UPersistentHashMap<UConcreteHeapRef, EtsMethod> = persistentHashMapOf(),
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

    fun getOrPutSortForLocal(idx: Int, localType: EtsType): USort {
        val localToSort = localToSortStack.last()
        val (updated, result) = localToSort.getOrPut(idx, ownership) { ctx.typeToSort(localType) }
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
        instance: EtsLocal?,
        args: List<EtsLocal>,
        localToIdx: (EtsMethod, EtsValue) -> Int?,
    ) {
        val argSorts = args.map { arg ->
            val argIdx = localToIdx(lastEnteredMethod, arg)
                ?: error("Arguments must present in the locals, but $arg is absent")
            getOrPutSortForLocal(argIdx, arg.type)
        }

        val instanceIdx = instance?.let { localToIdx(lastEnteredMethod, it) }
        val instanceSort = instanceIdx?.let { getOrPutSortForLocal(it, instance.type) }

        // Note: first, push an empty map, then fill the arguments, and then the instance (this)
        pushLocalToSortStack()
        argSorts.forEachIndexed { index, sort ->
            saveSortForLocal(index, sort)
        }
        instanceSort?.let { saveSortForLocal(args.size, it) }
    }

    fun pushSortsForActualArguments(
        arguments: List<UExpr<*>>,
    ) {
        pushLocalToSortStack()
        arguments.forEachIndexed { index, arg ->
            val idx = index
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
        promiseStates = promiseStates.put(promise, state, ownership)
    }

    fun setPromiseExecutor(
        promise: UConcreteHeapRef,
        method: EtsMethod,
    ) {
        promiseExecutors = promiseExecutors.put(promise, method, ownership)
    }

    fun getMethodRef(
        method: EtsMethod,
    ): UConcreteHeapRef {
        val (updated, result) = method2ref.getOrPut(method, ownership) {
            // TODO: what type should we use here?
            memory.allocConcrete(EtsUnknownType)
        }
        associatedMethods = associatedMethods.put(result, method, ownership)
        method2ref = updated
        return result
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
            globalObject = globalObject,
            addedArtificialLocals = addedArtificialLocals,
            lValuesToAllocatedFakeObjects = lValuesToAllocatedFakeObjects.toMutableList(),
            discoveredCallees = discoveredCallees,
            promiseStates = promiseStates,
            promiseExecutors = promiseExecutors,
            method2ref = method2ref,
            associatedMethods = associatedMethods,
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is TsMethodResult.TsException
}
