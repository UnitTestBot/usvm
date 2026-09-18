package org.usvm.machine.types

import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsContext

/**
 * Type metadata for a synthetic wrapper representing a TypeScript value whose runtime kind is not known.
 *
 * The wrapper is identified by a special concrete heap reference, but that reference is only the wrapper's storage
 * identity. It is not the object reference represented by the value. The possible boolean, number, and reference
 * payloads are stored separately in the wrapper's intermediate fields.
 *
 * [boolTypeExpr], [fpTypeExpr], and [refTypeExpr] are symbolic discriminators. Exactly one of them must be true for
 * every feasible state. Consumers should therefore keep the wrapper intact until the runtime kind is proven. In
 * particular, using the reference payload requires constraining [refTypeExpr] and then extracting that payload;
 * treating the wrapper reference itself as the payload or narrowing solely from a static TypeScript type is unsound.
 *
 * If narrowing establishes that the represented value is a particular object, the corresponding discriminator
 * constraints must also be propagated to previously materialized fake values that may refer to the same object.
 * Constraining only the extracted address breaks alias consistency.
 */
class EtsFakeType(
    val boolTypeExpr: UBoolExpr,
    val fpTypeExpr: UBoolExpr,
    val refTypeExpr: UBoolExpr,
    // TODO string,
) : EtsType {
    override val typeName: String
        get() = "FakeType"

    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }

    fun mkExactlyOneTypeConstraint(ctx: TsContext): UBoolExpr = with(ctx) {
        return mkAnd(
            mkImplies(boolTypeExpr, mkNot(fpTypeExpr)),
            mkImplies(boolTypeExpr, mkNot(refTypeExpr)),
            mkImplies(fpTypeExpr, mkNot(refTypeExpr)),
            mkOr(boolTypeExpr, fpTypeExpr, refTypeExpr),
        )
    }

    companion object {
        fun mkBool(ctx: TsContext): EtsFakeType {
            return EtsFakeType(ctx.mkTrue(), ctx.mkFalse(), ctx.mkFalse())
        }

        fun mkFp(ctx: TsContext): EtsFakeType {
            return EtsFakeType(ctx.mkFalse(), ctx.mkTrue(), ctx.mkFalse())
        }

        fun mkRef(ctx: TsContext): EtsFakeType {
            return EtsFakeType(ctx.mkFalse(), ctx.mkFalse(), ctx.mkTrue())
        }
    }
}

data class ExprWithTypeConstraint<Sort : USort>(
    val constraint: UBoolExpr,
    val expr: UExpr<Sort>,
)
