package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRefType
import org.usvm.UExpr
import org.usvm.UIteExpr

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleCast(
    expr: EtsCastExpr,
): UExpr<*>? = with(ctx) {
    val resolvedExpr = resolve(expr.arg) ?: return@with null
    return when (resolvedExpr.sort) {
        fp64Sort -> {
            logger.error("Unsupported cast from fp ${expr.arg} to ${expr.type}")
            TODO("Not yet implemented https://github.com/UnitTestBot/usvm/issues/299")
        }

        boolSort -> {
            logger.error("Unsupported cast from boolean ${expr.arg} to ${expr.type}")
            TODO("Not yet implemented https://github.com/UnitTestBot/usvm/issues/299")
        }

        addressSort -> {
            val instance = resolvedExpr.asExpr(addressSort)

            check(instance !is UIteExpr<*>) {
                "Casting from ITE expressions is not supported: $expr"
            }

            // If instance is fake object, we CAN cast, by imposing additional type constraints:
            if (instance.isFakeObject()) {
                val fakeType = instance.getFakeType(scope)
                if (expr.type is EtsNumberType) {
                    scope.assert(fakeType.fpTypeExpr) ?: run {
                        logger.warn { "UNSAT after ensuring casted fake object is a number" }
                        return null
                    }
                    return instance.extractFp(scope)
                }
                if (expr.type is EtsBooleanType) {
                    scope.assert(fakeType.boolTypeExpr) ?: run {
                        logger.warn { "UNSAT after ensuring casted fake object is a boolean" }
                        return null
                    }
                    return instance.extractBool(scope)
                }
                if (expr.type is EtsRefType) {
                    val condition = rewrap(scope, instance) {
                        scope.calcOnState { memory.types.evalIsSubtype(it, expr.type) }
                    } ?: return null
                    scope.assert(condition) ?: run {
                        logger.warn { "UNSAT after ensuring casted fake object is a subtype of ${expr.type}" }
                        return null
                    }
                }
            }

            if (expr.type !is EtsRefType) {
                // TODO: https://github.com/UnitTestBot/usvm/issues/299"
                logger.error { "Unsupported cast from non-ref ${expr.arg} to ${expr.type}" }
                scope.assert(falseExpr)
                return null
            }

            val condition = rewrap(scope, instance) {
                scope.calcOnState { memory.types.evalIsSubtype(it, expr.type) }
            } ?: return null
            scope.assert(condition) ?: run {
                logger.warn { "UNSAT after ensuring instance is of expected type" }
                return null
            }
            instance
        }

        else -> {
            error("Unsupported cast from ${expr.arg} to ${expr.type}")
        }
    }
}
