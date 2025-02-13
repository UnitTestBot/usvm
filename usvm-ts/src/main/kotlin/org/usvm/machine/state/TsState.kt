package org.usvm.machine.state

import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethod
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.USort
import org.usvm.UState
import org.usvm.api.targets.TsTarget
import org.usvm.collections.immutable.getOrPut
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.TsContext
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet
import org.usvm.collections.immutable.persistentHashMapOf

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
    private var localToSortStack: MutableList<UPersistentHashMap<Int, USort>> = mutableListOf(persistentHashMapOf()),
) : UState<EtsType, EtsMethod, EtsStmt, TsContext, TsTarget, TsState>(
    ctx,
    ownership,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    targets
) {
    fun getOrPutSortForLocal(localIdx: Int, localType: EtsType): USort {
        val localToSort = localToSortStack.last()
        val (updatedIndices, result) = localToSort.getOrPut(localIdx, ownership) { ctx.typeToSort(localType) }
        localToSortStack[localToSortStack.lastIndex] = updatedIndices
        return result
    }

    fun saveSortForLocal(localIdx: Int, sort: USort) {
        val localToSort = localToSortStack.last()
        val updatedSorts = localToSort.put(localIdx, sort, ownership)
        localToSortStack[localToSortStack.lastIndex] = updatedSorts
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
            getOrPutSortForLocal(localIdx, arg.type)
        }

        val instanceIdx = instance?.let { localToIdx(lastEnteredMethod, it) }
        val instanceSort = instanceIdx?.let { getOrPutSortForLocal(it, instance.type) }

        pushLocalToSortStack()
        argSorts.forEachIndexed { index, sort ->
            saveSortForLocal(index, sort)
        }
        instanceSort?.let { saveSortForLocal(args.size, it) }
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
            ctx,
            cloneOwnership,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints, newThisOwnership, cloneOwnership),
            models,
            pathNode,
            forkPoints,
            methodResult,
            targets.clone(),
            localToSortStack.toMutableList(),
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is TsMethodResult.TsException
}
