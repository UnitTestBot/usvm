package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.UExpr
import org.usvm.UIteExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.mkFakeValue

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleCast(
    expr: EtsCastExpr,
): UExpr<*>? = with(ctx) {
    val resolvedExpr = resolve(expr.arg) ?: return null
    return processCast(scope, resolvedExpr, expr.type)
}

fun TsContext.processCast(
    scope: TsStepScope,
    expr: UExpr<*>,
    type: EtsType,
): UExpr<*>? {
    when (expr.sort) {
        fp64Sort -> {
            // Casting number to something
            when (type) {
                is EtsAnyType -> {
                    // number as any - wrap in fake object without type constraint
                    return scope.calcOnState {
                        mkFakeValue(scope, fpValue = expr.asExpr(fp64Sort))
                    }
                }

                is EtsNumberType -> {
                    // number as number - identity cast
                    return expr
                }

                is EtsBooleanType -> {
                    // number as boolean - invalid cast in TypeScript
                    logger.warn { "Invalid cast from number to boolean: $expr to $type" }
                    scope.assert(falseExpr)
                    return null
                }

                is EtsRefType -> {
                    // number as SomeObject - invalid cast
                    logger.warn { "Invalid cast from number to ref type: $expr to $type" }
                    scope.assert(falseExpr)
                    return null
                }

                else -> {
                    // number as <other> - try wrapping in fake object
                    return scope.calcOnState {
                        mkFakeValue(scope, fpValue = expr.asExpr(fp64Sort))
                    }
                }
            }
        }

        boolSort -> {
            // Casting boolean to something
            when (type) {
                is EtsAnyType -> {
                    // boolean as any - wrap in fake object without type constraint
                    return scope.calcOnState {
                        mkFakeValue(scope, boolValue = expr.asExpr(boolSort))
                    }
                }

                is EtsBooleanType -> {
                    // boolean as boolean - identity cast
                    return expr
                }

                is EtsNumberType -> {
                    // boolean as number - invalid cast in TypeScript
                    logger.warn { "Invalid cast from boolean to number: $expr to $type" }
                    scope.assert(falseExpr)
                    return null
                }

                is EtsRefType -> {
                    // boolean as SomeObject - invalid cast
                    logger.warn { "Invalid cast from boolean to ref type: $expr to $type" }
                    scope.assert(falseExpr)
                    return null
                }

                else -> {
                    // boolean as <other> - try wrapping in fake object
                    return scope.calcOnState {
                        mkFakeValue(scope, boolValue = expr.asExpr(boolSort))
                    }
                }
            }
        }

        addressSort -> {
            val instance = expr.asExpr(addressSort)

            check(instance !is UIteExpr<*>) {
                "Casting from ITE expressions is not supported: $instance"
            }

            // If instance is fake object, we CAN cast, by imposing additional type constraints:
            if (instance.isFakeObject()) {
                val fakeType = instance.getFakeType(scope)
                if (type is EtsNumberType) {
                    scope.assert(fakeType.fpTypeExpr) ?: run {
                        logger.warn { "UNSAT after ensuring casted fake object is a number" }
                        return null
                    }
                    return instance.extractFp(scope)
                }
                if (type is EtsBooleanType) {
                    scope.assert(fakeType.boolTypeExpr) ?: run {
                        logger.warn { "UNSAT after ensuring casted fake object is a boolean" }
                        return null
                    }
                    return instance.extractBool(scope)
                }
                if (type is EtsAnyType) {
                    // Casting fake object to any - just return the fake object as is
                    return instance
                }
                if (type is EtsRefType || type is EtsStringType || type is EtsStringLiteralType) {
                    val condition = rewrap(scope, instance) {
                        scope.calcOnState { memory.types.evalIsSubtype(it, type) }
                    } ?: return null
                    scope.assert(condition) ?: run {
                        logger.warn { "UNSAT after ensuring casted fake object is a subtype of $type" }
                        return null
                    }
                    // Return the fake object itself, not the extracted ref
                    return instance
                }
            }

            // Non-fake object handling
            when (type) {
                is EtsAnyType -> {
                    // ref as any - wrap in fake object
                    return scope.calcOnState {
                        mkFakeValue(scope, refValue = instance)
                    }
                }

                is EtsRefType, is EtsStringType, is EtsStringLiteralType -> {
                    // ref as SomeRefType / ref as string - check subtype constraint
                    val condition = rewrap(scope, instance) {
                        scope.calcOnState { memory.types.evalIsSubtype(it, type) }
                    } ?: return null
                    scope.assert(condition) ?: run {
                        logger.warn { "UNSAT after ensuring instance is of expected type" }
                        return null
                    }
                    return instance
                }

                is EtsNumberType -> {
                    // ref as number - invalid unless it's a fake object (handled above)
                    logger.warn { "Invalid cast from ref to number: $expr to $type" }
                    scope.assert(falseExpr)
                    return null
                }

                is EtsBooleanType -> {
                    // ref as boolean - invalid unless it's a fake object (handled above)
                    logger.warn { "Invalid cast from ref to boolean: $expr to $type" }
                    scope.assert(falseExpr)
                    return null
                }

                else -> {
                    logger.error { "Unsupported cast from ref $expr to $type" }
                    scope.assert(falseExpr)
                    return null
                }
            }
        }

        else -> {
            logger.error { "Unsupported cast from unknown sort ${expr.sort}: $expr to $type" }
            scope.assert(falseExpr)
            return null
        }
    }
}
