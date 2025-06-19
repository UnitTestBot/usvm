package org.usvm.api

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UNullRef
import org.usvm.USort
import org.usvm.UState
import org.usvm.constraints.UTypeEvaluator
import org.usvm.memory.mapWithStaticAsConcrete
import org.usvm.types.UTypeStream
import org.usvm.types.singleOrNull
import org.usvm.uctx

fun <Type> UTypeEvaluator<Type>.evalTypeEquals(ref: UHeapRef, type: Type): UBoolExpr =
    with(ref.uctx) {
        mkAnd(
            evalIsSubtype(ref, type),
            evalIsSupertype(ref, type)
        )
    }

fun <Type> UState<Type, *, *, *, *, *>.objectTypeEquals(
    lhs: UHeapRef,
    rhs: UHeapRef
): UBoolExpr = with(lhs.uctx) {
    mapTypeStream(
        ref = lhs,
        onNull = {
            // type(null) = type(null); type(null) <: T /\ T <: type(null) ==> true /\ false
            mapTypeStream(rhs, onNull = { trueExpr }, { _, _ -> falseExpr })
        },
        operation = { lhsRef, lhsTypes ->
            mapTypeStream(
                rhs,
                onNull = { falseExpr },
                operation = { rhsRef, rhsTypes ->
                    mkTypeEqualsConstraint(lhsRef, lhsTypes, rhsRef, rhsTypes)
                }
            )
        }
    )
}

fun <Type> UState<Type, *, *, *, *, *>.objectTypeSubtype(
    lhs: UHeapRef,
    rhs: UHeapRef
): UBoolExpr = with(lhs.uctx) {
    mapTypeStream(
        ref = lhs,
        onNull = { trueExpr },
        operation = { lhsRef, lhsTypes ->
            mapTypeStream(
                rhs,
                onNull = { falseExpr },
                operation = { rhsRef, rhsTypes ->
                    mkSubtypeConstraint(lhsRef, lhsTypes, rhsRef, rhsTypes)
                }
            )
        }
    )
}

fun <Type, R : USort> UState<Type, *, *, *, *, *>.mapTypeStreamNotNull(
    ref: UHeapRef,
    operation: (UHeapRef, UTypeStream<Type>) -> UExpr<R>?
): UExpr<R>? = mapTypeStream(
    ref = ref,
    onNull = { error("unexpected null") },
    operation = { expr, types ->
        operation(expr, types) ?: return null
    },
    ignoreNullRefs = true
)

fun <Type, R : USort> UState<Type, *, *, *, *, *>.mapTypeStream(
    ref: UHeapRef,
    operation: (UHeapRef, UTypeStream<Type>) -> UExpr<R>?
): UExpr<R>? = mapTypeStream(
    ref = ref,
    onNull = { return null },
    operation = { expr, types ->
        operation(expr, types) ?: return null
    }
)

private fun <Type> UState<Type, *, *, *, *, *>.mkTypeEqualsConstraint(
    lhs: UHeapRef,
    lhsTypes: UTypeStream<Type>,
    rhs: UHeapRef,
    rhsTypes: UTypeStream<Type>,
): UBoolExpr = with(lhs.uctx) {
    val lhsType = lhsTypes.singleOrNull()
    val rhsType = rhsTypes.singleOrNull()

    if (lhsType != null) {
        return if (lhsType == rhsType) {
            trueExpr
        } else {
            memory.types.evalTypeEquals(rhs, lhsType)
        }
    }

    if (rhsType != null) {
        return memory.types.evalTypeEquals(lhs, rhsType)
    }

    // TODO: don't mock type equals
    makeSymbolicPrimitive(boolSort)
}

private fun <Type> UState<Type, *, *, *, *, *>.mkSubtypeConstraint(
    lhs: UHeapRef,
    lhsTypes: UTypeStream<Type>,
    rhs: UHeapRef,
    rhsTypes: UTypeStream<Type>,
): UBoolExpr = with(lhs.uctx) {
    val lhsType = lhsTypes.singleOrNull()
    val rhsType = rhsTypes.singleOrNull()

    if (lhsType != null) {
        return if (lhsType == rhsType) {
            trueExpr
        } else {
            memory.types.evalIsSupertype(rhs, lhsType)
        }
    }

    if (rhsType != null) {
        return memory.types.evalIsSubtype(lhs, rhsType)
    }

    // TODO: don't mock type equals
    makeSymbolicPrimitive(boolSort)
}

private inline fun <Type, R : USort> UState<Type, *, *, *, *, *>.mapTypeStream(
    ref: UHeapRef,
    onNull: () -> UExpr<R>,
    operation: (UHeapRef, UTypeStream<Type>) -> UExpr<R>,
    ignoreNullRefs: Boolean = false,
): UExpr<R> = ref.mapWithStaticAsConcrete(
    ignoreNullRefs = ignoreNullRefs,
    concreteMapper = { concreteRef ->
        val types = memory.types.getTypeStream(concreteRef)
        operation(concreteRef, types)
    },
    symbolicMapper = { symbolicRef ->
        if (symbolicRef is UNullRef) {
            onNull()
        } else {
            val types = memory.types.getTypeStream(symbolicRef)
            operation(symbolicRef, types)
        }
    },
)
