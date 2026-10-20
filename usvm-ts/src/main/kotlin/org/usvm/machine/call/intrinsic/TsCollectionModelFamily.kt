package org.usvm.machine.call.intrinsic

import org.usvm.UConcreteHeapRef
import org.usvm.isFalse
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.expr.MAX_MODELED_COLLECTION_ENTRIES
import org.usvm.machine.expr.TsCollectionKind
import org.usvm.machine.expr.collectionClear
import org.usvm.machine.expr.collectionGet
import org.usvm.machine.expr.collectionInsert
import org.usvm.machine.expr.collectionMatches
import org.usvm.machine.expr.collectionPut
import org.usvm.machine.expr.collectionRemove
import org.usvm.machine.expr.collectionUndefined
import org.usvm.machine.expr.collectionValue
import org.usvm.machine.state.TsState
import org.usvm.machine.types.iteUnresolvedValue

/** A bounded coherent subset of native Map/Set operations. */
internal object TsCollectionModelFamily : TsBuiltInUnknownCallModelFamily {

    override val models: List<TsUnknownCallModel> = listOf(
        OperationModel(operation = "get"),
        OperationModel(operation = "set"),
        OperationModel(operation = "add"),
        OperationModel(operation = "has"),
        OperationModel(operation = "delete"),
        OperationModel(operation = "clear"),
    )

    private class OperationModel(private val operation: String) : TsUnknownCallModel {
        override val id = "ts.collection.$operation"
        override val target = TsUnknownCallTarget(methodName = operation)

        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? = with(state.ctx) {
            val receiver = call.receiver?.resolved as? UConcreteHeapRef ?: return null
            val collection = state.modeledCollections[receiver] ?: return null
            val originalReceiver = state.collectionOriginalReceivers[receiver] ?: receiver
            val mapOnly = operation == "get" || operation == "set"
            if (mapOnly && collection.kind != TsCollectionKind.MAP) return null
            if (operation == "add" && collection.kind != TsCollectionKind.SET) return null
            val arguments = call.arguments.map { it.resolved ?: return null }
            if (operation == "clear") {
                val successor = TsUnknownCallModelSuccessor(
                    guard = trueExpr,
                    completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
                    applyStateChanges = {
                        collectionClear(receiver, collection)
                    },
                )
                return TsUnknownCallModelExecution(successors = listOf(successor))
            }
            val key = state.collectionValue(arguments.firstOrNull() ?: mkUndefinedValue())
            val matches = state.collectionMatches(collection, key) ?: return null
            val found = mkOr(matches.map { it.guard })
            val missing = mkNot(found)
            when (operation) {
                "has" -> {
                    val successor = TsUnknownCallModelSuccessor(
                        guard = trueExpr,
                        completion = TsUnknownCallModelCompletion.Normal { found },
                    )
                    TsUnknownCallModelExecution(successors = listOf(successor))
                }
                "get" -> {
                    var result = state.collectionUndefined()
                    for (match in matches) {
                        result = iteUnresolvedValue(match.guard, state.collectionGet(collection, match.key), result)
                    }
                    val completion = TsUnknownCallModelCompletion.Unresolved(result)
                    val successor = TsUnknownCallModelSuccessor(guard = trueExpr, completion = completion)
                    TsUnknownCallModelExecution(successors = listOf(successor))
                }
                "delete" -> {
                    val successors = matches.map { match ->
                        TsUnknownCallModelSuccessor(
                            guard = match.guard,
                            completion = TsUnknownCallModelCompletion.Normal { ctx.trueExpr },
                            applyStateChanges = { collectionRemove(receiver, collection, match.key) },
                        )
                    } + TsUnknownCallModelSuccessor(
                        guard = missing,
                        completion = TsUnknownCallModelCompletion.Normal { ctx.falseExpr },
                    )
                    TsUnknownCallModelExecution(successors)
                }
                "set", "add" -> {
                    val value = if (operation == "add") {
                        key
                    } else {
                        state.collectionValue(arguments.getOrNull(1) ?: mkUndefinedValue())
                    }
                    val successors = matches.map { match ->
                        TsUnknownCallModelSuccessor(
                            guard = match.guard,
                            completion = TsUnknownCallModelCompletion.Normal { originalReceiver },
                            applyStateChanges = {
                                if (collection.kind == TsCollectionKind.MAP) collectionPut(collection, match.key, value)
                            },
                        )
                    }.toMutableList()
                    val canInsert = collection.keys.size < MAX_MODELED_COLLECTION_ENTRIES
                    if (canInsert) {
                        successors += TsUnknownCallModelSuccessor(
                            guard = missing,
                            completion = TsUnknownCallModelCompletion.Normal { originalReceiver },
                            applyStateChanges = { collectionInsert(receiver, collection, key, value) },
                        )
                    }
                    if (successors.isEmpty()) return null
                    TsUnknownCallModelExecution(
                        successors = successors,
                        residualGuard = if (canInsert || missing.isFalse) null else missing,
                    )
                }
                else -> error("Unexpected collection operation: $operation")
            }
        }
    }
}
