package org.usvm

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

interface UTypeSystem<Type> {
    // Returns true if t <: u
    fun isSupertype(u: Type, t: Type): Boolean
}

interface UTypeEvaluator<Type> {
    fun evalIs(ref: UHeapRef, type: Type): UBoolExpr
}

class UTypeModel<Type>(
    private val ctx: UContext,
    private val typeSystem: UTypeSystem<Type>,
    private val typeByAddr: Map<UConcreteHeapAddress, Type>,
) : UTypeEvaluator<Type> {

    fun typeOf(address: UConcreteHeapAddress): Type = typeByAddr.getValue(address)

    override fun evalIs(ref: UHeapRef, type: Type): UBoolExpr =
        when (ref) {
            is UConcreteHeapRef -> {
                val holds = typeSystem.isSupertype(type, typeOf(ref.address))
                if (holds) ctx.trueExpr else ctx.falseExpr
            }

            else -> throw IllegalArgumentException("Expecting concrete ref, but got $ref")
        }
}

class UTypeStorage<Type>(
    private val ctx: UContext,
    val typeSystem: UTypeSystem<Type>,
    val isContraditing: Boolean = false,
    val concreteTypes: PersistentMap<UConcreteHeapAddress, Type> = persistentMapOf(),
    val supertypes: PersistentMap<UHeapRef, PersistentSet<Type>> = persistentMapOf(),
) : UTypeEvaluator<Type> {

    fun contradiction() =
        UTypeStorage(ctx, typeSystem, true, concreteTypes, supertypes)

    fun allocate(ref: UConcreteHeapAddress, type: Type): UTypeStorage<Type> =
        UTypeStorage(ctx, typeSystem, isContraditing, concreteTypes.put(ref, type), supertypes)

    fun cast(ref: UHeapRef, type: Type): UTypeStorage<Type> {
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteTypes.getValue(ref.address)
                if (!typeSystem.isSupertype(type, concreteType))
                    return contradiction()
                else
                    return this
            }

            else -> {
                val constraints = supertypes.getOrDefault(ref, persistentSetOf())
                // TODO: check if we have simple contradiction here
                return UTypeStorage(
                    ctx,
                    typeSystem,
                    isContraditing,
                    concreteTypes,
                    supertypes.put(ref, constraints.add(type))
                )
            }
        }
    }

    override fun evalIs(ref: UHeapRef, type: Type): UBoolExpr {
        when (ref) {
            is UConcreteHeapRef -> {
                val concreteType = concreteTypes.getValue(ref.address)
                return if (typeSystem.isSupertype(type, concreteType)) ctx.trueExpr else ctx.falseExpr
            }

            else -> {
                @Suppress("UNUSED_VARIABLE")
                val constraints = supertypes.getOrDefault(ref, persistentSetOf())
                // TODO: check if we have simple contradiction here and return false if we do
                return ctx.mkIsExpr(ref, type)
            }
        }
    }
}
