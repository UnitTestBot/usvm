package org.usvm.machine.expr

import mu.KotlinLogging
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.UExpr
import org.usvm.machine.Constants
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.expr.TsExprApproximationResult.NoApproximation
import org.usvm.machine.expr.TsExprApproximationResult.ResolveFailure
import org.usvm.machine.expr.TsExprApproximationResult.SuccessfulApproximation
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.util.TsResolutionResult

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleStaticCall(
    expr: EtsStaticCallExpr,
): UExpr<*>? = with(ctx) {
    // Check if the method was already called and returned a value.
    when (val result = scope.calcOnState { methodResult }) {
        is TsMethodResult.Success -> {
            scope.doWithState { methodResult = TsMethodResult.NoCall }
            return result.value
        }

        is TsMethodResult.TsException -> {
            error("Exception should be handled earlier")
        }

        is TsMethodResult.NoCall -> {} // proceed to call
    }

    // Try to approximate the call.
    when (val result = tryApproximateStaticCall(expr)) {
        is SuccessfulApproximation -> return result.expr
        is ResolveFailure -> return null
        is NoApproximation -> {}
    }

    // Resolve the static method.
    when (val resolved = resolveStaticMethod(expr.callee)) {
        is TsResolutionResult.Empty -> {
            logger.error { "Could not resolve static call: ${expr.callee}" }
            scope.assert(falseExpr) ?: return null
        }

        is TsResolutionResult.Ambiguous -> {
            processAmbiguousStaticMethod(resolved, expr)
        }

        is TsResolutionResult.Unique -> {
            processUniqueStaticMethod(resolved, expr)
        }
    }

    // Return null to indicate that we are awaiting the call to be executed.
    null
}

private fun TsExprResolver.resolveStaticMethod(
    method: EtsMethodSignature,
): TsResolutionResult<EtsMethod> {
    // Perfect signature:
    if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
        val classes = hierarchy.classesForType(EtsClassType(method.enclosingClass))
        if (classes.size > 1) {
            val methods = classes.map { it.methods.single { it.name == method.name } }
            return TsResolutionResult.create(methods)
        }

        if (classes.isEmpty()) return TsResolutionResult.Empty

        val clazz = classes.single()
        val methods = clazz.methods.filter { it.name == method.name }
        return TsResolutionResult.create(methods)
    }

    // Unknown signature:
    val methods = ctx.scene.projectAndSdkClasses
        .flatMap { it.methods }
        .filter { it.name == method.name }

    return TsResolutionResult.create(methods)
}

private fun TsExprResolver.processAmbiguousStaticMethod(
    resolved: TsResolutionResult.Ambiguous<EtsMethod>,
    expr: EtsStaticCallExpr,
) {
    val args = expr.args.map { resolve(it) ?: return }
    val staticProperties = resolved.properties.take(Constants.STATIC_METHODS_FORK_LIMIT)
    val staticInstances = scope.calcOnState {
        staticProperties.map { getStaticInstance(it.enclosingClass!!) }
    }
    val concreteCalls = staticProperties.mapIndexed { index, value ->
        TsConcreteMethodCallStmt(
            callee = value,
            instance = staticInstances[index],
            args = args,
            returnSite = scope.calcOnState { lastStmt }
        )
    }
    scope.forkMulti(concreteCalls.map { stmt ->
        ctx.mkTrue() to { newStmt(stmt) }
    })
}

private fun TsExprResolver.processUniqueStaticMethod(
    resolved: TsResolutionResult.Unique<EtsMethod>,
    expr: EtsStaticCallExpr,
) {
    val instance = scope.calcOnState {
        getStaticInstance(resolved.property.enclosingClass!!)
    }
    val args = expr.args.map { resolve(it) ?: return }
    val concreteCall = TsConcreteMethodCallStmt(
        callee = resolved.property,
        instance = instance,
        args = args,
        returnSite = scope.calcOnState { lastStmt },
    )
    scope.doWithState { newStmt(concreteCall) }
}
