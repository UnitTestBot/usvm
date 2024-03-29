package org.usvm.model

import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.constraints.UTypeEvaluator
import org.usvm.types.UTypeRegion
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

class UTypeModel<Type>(
    val typeSystem: UTypeSystem<Type>,
    typeRegionByAddr: Map<UConcreteHeapAddress, UTypeRegion<Type>>,
) : UTypeEvaluator<Type> {
    private val typeStreamByAddr = typeRegionByAddr.toMutableMap()

    private fun typeRegion(ref: UConcreteHeapRef): UTypeRegion<Type> =
        typeStreamByAddr[ref.address] ?: UTypeRegion(typeSystem, typeSystem.topTypeStream())

    override fun evalIsSubtype(ref: UHeapRef, supertype: Type): UBoolExpr =
        when {
            ref is UConcreteHeapRef && ref.address == NULL_ADDRESS -> ref.ctx.trueExpr

            ref is UConcreteHeapRef -> {
                // All the expressions in the model are interpreted, therefore, they must
                // have concrete addresses. Moreover, the model knows only about input values
                // which have addresses less or equal than INITIAL_INPUT_ADDRESS
                require(ref.address <= INITIAL_INPUT_ADDRESS) { "Unexpected ref: $ref" }

                val evaluatedTypeRegion = typeRegion(ref)
                val updatedTypeRegion = evaluatedTypeRegion.addSupertype(supertype)
                val updatedTypeStream = updatedTypeRegion.typeStream
                val isEmpty = updatedTypeStream.isEmpty
                    ?: error("Type stream exceeded ${typeSystem.typeOperationsTimeout} timeout on supertype $supertype")
                if (!isEmpty) {
                    typeStreamByAddr[ref.address] = updatedTypeRegion
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
                require(ref.address <= INITIAL_INPUT_ADDRESS) { "Unexpected ref: $ref" }

                val evaluatedTypeRegion = typeRegion(ref)
                val updatedTypeRegion = evaluatedTypeRegion.addSubtype(subtype)
                val updatedTypeStream = updatedTypeRegion.typeStream
                val isEmpty = updatedTypeStream.isEmpty
                    ?: error("Type stream exceeded ${typeSystem.typeOperationsTimeout} timeout on subtype $subtype")
                if (!isEmpty) {
                    typeStreamByAddr[ref.address] = updatedTypeRegion
                    ref.ctx.trueExpr
                } else {
                    ref.ctx.falseExpr
                }
            }

            else -> error("Expecting concrete ref, but got $ref")
        }

    override fun getTypeStream(ref: UHeapRef): UTypeStream<Type> {
        check(ref is UConcreteHeapRef) { "Unexpected ref: $ref" }
        return typeRegion(ref).typeStream
    }
}
