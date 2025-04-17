package org.usvm.machine.state

import org.jacodb.ets.model.EtsClass
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
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet
import org.usvm.util.type

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
    fun getOrPutSortForLocal(localIdx: Int, localType: EtsType): USort {
        val localToSort = localToSortStack.last()
        val (updated, result) = localToSort.getOrPut(localIdx, ownership) { ctx.typeToSort(localType) }
        localToSortStack[localToSortStack.lastIndex] = updated
        return result
    }

    fun saveSortForLocal(localIdx: Int, sort: USort) {
        val localToSort = localToSortStack.last()
        val updated = localToSort.put(localIdx, sort, ownership)
        localToSortStack[localToSortStack.lastIndex] = updated
    }

    fun pushLocalToSortStack() {
        localToSortStack.add(persistentHashMapOf())
    }

    fun popLocalToSortStack() {
        localToSortStack.removeLast()
    }

    fun pushSortsForArguments(instance: EtsLocal?, args: List<EtsValue>, localToIdx: (EtsMethod, EtsValue) -> Int) {
        val argSorts = args.map { arg ->
            val localIdx = localToIdx(lastEnteredMethod, arg)
            getOrPutSortForLocal(localIdx, if (arg is EtsLocal) arg.type else EtsUnknownType) // TODO: type
        }

        val instanceIdx = instance?.let { localToIdx(lastEnteredMethod, it) }
        val instanceSort = instanceIdx?.let { getOrPutSortForLocal(it, instance.type) }

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
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is TsMethodResult.TsException
}
