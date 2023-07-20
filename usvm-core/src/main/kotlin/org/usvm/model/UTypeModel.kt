package org.usvm.model

import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.constraints.UTypeEvaluator
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

class UTypeModel<Type>(
    val typeSystem: UTypeSystem<Type>,
    typeStreamByAddr: Map<UConcreteHeapAddress, UTypeStream<Type>>,
) : UTypeEvaluator<Type> {
    private val typeStreamByAddr = typeStreamByAddr.toMutableMap()

    fun typeStream(ref: UConcreteHeapRef): UTypeStream<Type> =
        typeStreamByAddr[ref.address] ?: typeSystem.topTypeStream()

    override fun evalIsSubtype(ref: UHeapRef, supertype: Type): UBoolExpr =
        when {
            ref is UConcreteHeapRef && ref.address == NULL_ADDRESS -> ref.ctx.trueExpr

            ref is UConcreteHeapRef -> {
                // All the expressions in the model are interpreted, therefore, they must
                // have concrete addresses. Moreover, the model knows only about input values
                // which have addresses less or equal than INITIAL_INPUT_ADDRESS
                require(ref.address <= INITIAL_INPUT_ADDRESS)

                val evaluatedTypeStream = typeStream(ref)
                val typeStream = evaluatedTypeStream.filterBySupertype(supertype)
                if (!typeStream.isEmpty) {
                    typeStreamByAddr[ref.address] = typeStream
                    ref.ctx.trueExpr
                } else {
                    ref.ctx.falseExpr
                }
            }

            else -> error("Expecting concrete ref, but got $ref")
        }

    override fun evalIsSupertype(ref: UHeapRef, subtype: Type): UBoolExpr =
        when {
            ref is UConcreteHeapRef && ref.address == NULL_ADDRESS -> ref.ctx.falseExpr

            ref is UConcreteHeapRef -> {
                // All the expressions in the model are interpreted, therefore, they must
                // have concrete addresses. Moreover, the model knows only about input values
                // which have addresses less or equal than INITIAL_INPUT_ADDRESS
                require(ref.address <= INITIAL_INPUT_ADDRESS)

                val evaluatedTypeStream = typeStream(ref)
                val typeStream = evaluatedTypeStream.filterBySubtype(subtype)
                if (!typeStream.isEmpty) {
                    typeStreamByAddr[ref.address] = typeStream
                    ref.ctx.trueExpr
                } else {
                    ref.ctx.falseExpr
                }
            }

            else -> error("Expecting concrete ref, but got $ref")
        }
}
