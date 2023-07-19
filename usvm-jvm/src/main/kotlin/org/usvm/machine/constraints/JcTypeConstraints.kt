//package org.usvm.machine.constraints
//
//import org.jacodb.api.JcArrayType
//import org.jacodb.api.JcType
//import org.usvm.UBoolExpr
//import org.usvm.UConcreteHeapRef
//import org.usvm.UHeapRef
//import org.usvm.UNullRef
//import org.usvm.USymbolicHeapRef
//import org.usvm.constraints.UEqualityConstraints
//import org.usvm.constraints.UTypeConstraints
//import org.usvm.machine.JcTypeSystem
//import org.usvm.machine.jctx
//import org.usvm.memory.map
//
//class JcTypeConstraints(
//    private val typeSystem: JcTypeSystem,
//    private val equalityConstraints: UEqualityConstraints,
//) : UTypeConstraints<JcType>(typeSystem, equalityConstraints) {
//
//    fun evalIsElementOfArray(elemRef: UHeapRef, arrayRef: UHeapRef): UBoolExpr {
//        val ctx = elemRef.jctx
//        return elemRef.map(
//            concreteMapper = { concreteElemRef ->
//                val elemType = concreteRefToType.getValue(concreteElemRef.address)
//                arrayRef.map(
//                    concreteMapper = { concreteArrayRef ->
//                        val arrayType = concreteRefToType.getValue(concreteArrayRef.address)
//                        if (typeSystem.isArrayElemType(elemType, arrayType)) {
//                            ctx.trueExpr
//                        } else {
//                            ctx.falseExpr
//                        }
//                    },
//                    symbolicMapper = { symbolicArrayRef ->
//                        val arrayTypeRegion = getTypeRegion(symbolicArrayRef)
//                        val arrayTypeOfElement = typeSystem.arrayTypeOf(elemType)
//                        val newArrayTypeRegion = arrayTypeRegion.addSubtype(arrayTypeOfElement)
//                        if (newArrayTypeRegion.isEmpty) {
//                            ctx.falseExpr
//                        } else {
//                            evalIsSupertype(arrayRef, arrayTypeOfElement)
//                        }
//                    },
//                    ignoreNullRefs = true
//                )
//            },
//            symbolicMapper = mapper@{ symbolicElemRef ->
//                if (symbolicElemRef == ctx.nullRef) {
//                    return@mapper ctx.trueExpr
//                }
//
//                val elemTypeRegion = getTypeRegion(symbolicElemRef)
//                arrayRef.map(
//                    concreteMapper = { concreteArrayRef ->
//                        val concreteArrayType = concreteRefToType.getValue(concreteArrayRef.address) as JcArrayType
//                        evalIs(symbolicElemRef, concreteArrayType.elementType)
//                    },
//                    symbolicMapper = { symbolicArrayRef ->
//                        val arrayTypeRegion = getTypeRegion(symbolicArrayRef)
//                        // actually, we need to map type region here
//                        val newArrayTypeRegion = arrayTypeRegion.intersect(elemTypeRegion)
//                        if (newArrayTypeRegion.isEmpty) {
//                            ctx.falseExpr
//                        } else {
//                            ctx.mkElementOfArrayTypeExpr(elemRef, arrayRef)
//                        }
//                    },
//                    ignoreNullRefs = true
//                )
//            },
//            ignoreNullRefs = false
//        )
//    }
//
//    fun addIsElementOfArray(elemRef: UHeapRef, arrayRef: UHeapRef) {
//        when (elemRef) {
//            is UConcreteHeapRef -> {
//                val elemType = concreteRefToType.getValue(elemRef.address)
//                when (arrayRef) {
//                    is UConcreteHeapRef -> {
//                        val arrayType = concreteRefToType.getValue(arrayRef.address) as JcArrayType
//                        if (typeSystem.isArrayElemType(elemType, arrayType)) {
//                            contradiction()
//                        }
//                    }
//
//                    is UNullRef -> error("Null ref should be handled explicitly earlier")
//                    is USymbolicHeapRef -> {
//                        val arrayTypeOfElement = typeSystem.arrayTypeOf(elemType)
//                        updateRegionCannotBeEqualNull(arrayRef) { it.addSubtype(arrayTypeOfElement) }
//                    }
//
//                    else -> error("Unexpected ref: $arrayRef")
//                }
//            }
//
//            is UNullRef -> return
//            is USymbolicHeapRef -> {
//                when (arrayRef) {
//                    is UConcreteHeapRef -> {
//                        val arrayType = concreteRefToType.getValue(arrayRef.address) as JcArrayType
//                        updateRegionCanBeEqualNull(elemRef) { it.addSupertype(arrayType) }
//                    }
//
//                    is UNullRef -> error("Null ref should be handled explicitly earlier")
//                    is USymbolicHeapRef -> {
//                        val elemTypeRegion = getTypeRegion(elemRef)
//                        val arrayTypeRegion = getTypeRegion(arrayRef)
//                        // actually, we need to map type regions here
//                        updateRegionCanBeEqualNull(elemRef) { it.intersect(arrayTypeRegion) }
//                        // actually, we need to map type regions here
//                        updateRegionCannotBeEqualNull(arrayRef) { it.intersect(elemTypeRegion) }
//                    }
//
//                    else -> error("Unexpected ref: $arrayRef")
//                }
//            }
//
//            else -> error("Unexpected ref: $arrayRef")
//        }
//    }
//}
