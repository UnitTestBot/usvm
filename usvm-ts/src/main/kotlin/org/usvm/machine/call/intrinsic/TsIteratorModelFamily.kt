package org.usvm.machine.call.intrinsic

import org.usvm.UConcreteHeapRef
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.expr.TsArrayIterator
import org.usvm.machine.expr.TsCollectionKind
import org.usvm.machine.expr.TsIteratorKind
import org.usvm.machine.expr.arrayIteratorType
import org.usvm.machine.expr.newCollectionIterator
import org.usvm.machine.state.TsState

/** Named native iterator methods; arbitrary project receivers do not satisfy the runtime domain. */
internal object TsIteratorModelFamily : TsBuiltInUnknownCallModelFamily {
    const val COLLECTION_ITERATOR_ID = "ts.iterator.collection"
    const val COLLECTION_ITERATOR_METHOD = "%collectionIterator"

    override val models: List<TsUnknownCallModel> =
        TsIteratorKind.entries.map { kind -> IteratorModel(kind) } + CollectionIteratorModel

    private object CollectionIteratorModel : TsUnknownCallModel {
        override val id = COLLECTION_ITERATOR_ID
        override val target = TsUnknownCallTarget(
            methodName = COLLECTION_ITERATOR_METHOD,
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        )

        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
            val receiver = call.receiver?.resolved as? UConcreteHeapRef ?: return null
            val collection = state.modeledCollections[receiver] ?: return null
            val kind = if (collection.kind == TsCollectionKind.MAP) TsIteratorKind.ENTRIES else TsIteratorKind.VALUES
            val successor = TsUnknownCallModelSuccessor(
                guard = state.ctx.trueExpr,
                completion = TsUnknownCallModelCompletion.Normal { newCollectionIterator(receiver, kind) },
            )
            return TsUnknownCallModelExecution(successors = listOf(successor))
        }
    }

    private class IteratorModel(private val kind: TsIteratorKind) : TsUnknownCallModel {
        override val id = "ts.iterator.${kind.name.lowercase()}"
        override val target = TsUnknownCallTarget(methodName = kind.name.lowercase())

        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
            val receiver = call.receiver?.resolved as? UConcreteHeapRef ?: return null
            val collection = state.modeledCollections[receiver]
            val array = if (collection == null) state.concreteArray(call, listOf(receiver)) else null
            if (collection == null && array == null) return null

            val successor = TsUnknownCallModelSuccessor(
                guard = state.ctx.trueExpr,
                completion = TsUnknownCallModelCompletion.Normal {
                    if (collection != null) {
                        newCollectionIterator(receiver, kind)
                    } else {
                        val arrayType = requireNotNull(array).second
                        val iterator = memory.allocConcrete(arrayIteratorType)
                        arrayIterators += iterator to TsArrayIterator(
                            array = receiver,
                            arrayType = arrayType,
                            index = ctx.mkBv(0),
                            exhausted = ctx.falseExpr,
                            kind = kind,
                        )
                        iterator
                    }
                },
            )
            return TsUnknownCallModelExecution(successors = listOf(successor))
        }
    }
}
