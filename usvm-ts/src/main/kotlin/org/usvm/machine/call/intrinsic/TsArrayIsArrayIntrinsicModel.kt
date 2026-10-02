package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.state.TsState

/** Exact `Array.isArray` semantics for the genuine global `Array` built-in. */
internal object TsArrayIsArrayIntrinsicModel : TsBuiltInUnknownCallModel {
    const val MODEL_ID: String = "ts.array.isArray"

    private val anyArrayType = EtsArrayType(elementType = EtsUnknownType, dimensions = 1)

    override val id: String = MODEL_ID
    override val target = TsUnknownCallTarget(
        methodName = "isArray",
    )

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
        if (!call.hasGlobalArrayOwner()) {
            return null
        }

        val value = when {
            call.arguments.isEmpty() -> return state.normalExecution(state.ctx.falseExpr)
            else -> call.arguments.first().resolved ?: return null
        }
        val result = state.isArray(value)

        return state.normalExecution(result)
    }

    private fun TsUnknownCall.hasGlobalArrayOwner(): Boolean {
        val owner = receiver?.source as? EtsLocal ?: return false
        val ownerType = owner.type as? EtsFunctionType ?: return false
        val signatureFile = ownerType.signature.enclosingClass.file

        return owner.name == "Array" &&
            ownerType.signature.returnType is EtsArrayType &&
            signatureFile.projectName == UNKNOWN_SIGNATURE_COMPONENT &&
            signatureFile.fileName == UNKNOWN_SIGNATURE_COMPONENT
    }

    private fun TsState.isArray(value: UExpr<*>) = with(ctx) {
        when {
            value.isFakeObject() -> {
                val fakeType = value.getFakeType(memory)
                val reference = value.extractRef(memory)

                mkAnd(
                    fakeType.refTypeExpr,
                    isNonNullArrayReference(reference),
                )
            }

            value.sort == addressSort -> isNonNullArrayReference(value.asExpr(addressSort))
            else -> falseExpr
        }
    }

    private fun TsState.isNonNullArrayReference(reference: UHeapRef): UBoolExpr = with(ctx) {
        mkAnd(
            mkNot(mkEq(reference, mkUndefinedValue())),
            mkNot(mkEq(reference, mkTsNullValue())),
            memory.types.evalIsSubtype(reference, anyArrayType),
        )
    }

    private fun TsState.normalExecution(result: UExpr<*>): TsUnknownCallModelExecution = with(ctx) {
        TsUnknownCallModelExecution(
            successors = listOf(
                TsUnknownCallModelSuccessor(
                    guard = trueExpr,
                    completion = TsUnknownCallModelCompletion.Normal { result },
                )
            )
        )
    }

    private const val UNKNOWN_SIGNATURE_COMPONENT: String = "%unk"
}
