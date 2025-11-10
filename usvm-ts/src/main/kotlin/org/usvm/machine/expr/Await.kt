package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UExpr
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.getResolvedValue
import org.usvm.machine.interpreter.isResolved
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.mapFake
import org.usvm.machine.types.mkFakeValue

internal fun TsExprResolver.handleAwait(
    expr: EtsAwaitExpr,
): UExpr<*>? = with(ctx) {
    val arg = resolve(expr.arg) ?: return null
    return processAwait(scope, arg, expr.arg.type, isTargetedMode = options.isTargetedModeEnabled)
}

fun TsContext.processAwait(
    scope: TsStepScope,
    arg: UExpr<*>,
    type: EtsType,
    isTargetedMode: Boolean = false,
): UExpr<*>? {
    // Awaiting primitives does nothing.
    if (arg.sort != addressSort) {
        return arg
    }
    // ...including null/undefined
    if (arg == mkTsNullValue() || arg == mkUndefinedValue()) {
        return arg
    }

    val promise = arg.asExpr(addressSort)

    // Awaiting fake objects.
    if (promise.isFakeObject()) {
        return scope.calcOnState {
            mapFake(
                scope = scope,
                fakeObject = promise,
                mapBool = { processAwait(scope, it, type, isTargetedMode)?.asExpr(boolSort) },
                mapFp = { processAwait(scope, it, type, isTargetedMode)?.asExpr(fp64Sort) },
                mapRef = { processAwait(scope, it, type, isTargetedMode)?.asExpr(addressSort) },
            )
        }
    }

    val isAllocated = isAllocatedConcreteHeapRef(promise)
    if (!isAllocated) {
        if (isTargetedMode) {
            // We cannot under-approximate in targeted mode
            return scope.calcOnState { mkFakeValue(scope, type = type) }
        } else {
            error("Promise instance should be allocated, but it is not: $promise")
        }
    }
    require(isAllocated)

    val promiseState = scope.calcOnState {
        promiseState[promise] ?: PromiseState.PENDING
    }

    val isResolved = scope.calcOnState { isResolved(promise) }
    return if (!isResolved) {
        // If the promise is not resolved yet, we need to call the executor to resolve it.
        check(promiseState == PromiseState.PENDING) {
            "Promise state should be PENDING, but it is $promiseState for $promise"
        }
        val executor = scope.calcOnState {
            promiseExecutor[promise]
                ?: error("Await expression should have a promise executor, but it is not set for $promise")
        }
        check(executor.cfg.stmts.isNotEmpty())

        val args: MutableList<UExpr<*>> = mutableListOf()

        // Executor lambda does not have 'this', so we fill it with 'undefined':
        args += mkUndefinedValue()

        val params = executor.parameters.toMutableList()
        if (params.isNotEmpty() && params[0].type is EtsLexicalEnvType) {
            params.removeFirst()
            // TODO: handle closures
            args += mkUndefinedValue()
        }
        if (params.isNotEmpty()) {
            args += resolveFunctionRef
            scope.doWithState {
                setBoundThis(resolveFunctionRef, promise)
            }
            if (params.size >= 2) {
                args += rejectFunctionRef
                scope.doWithState {
                    setBoundThis(rejectFunctionRef, promise)
                }
                if (params.size >= 3) {
                    error(
                        "Promise executor should have at most 3 parameters" +
                            " (closures, resolve, reject), but got ${params.size}"
                    )
                }
            }
        }
        scope.doWithState {
            pushSortsForActualArguments(args)
            memory.stack.push(args.toTypedArray(), executor.localsCount)
            registerCallee(currentStatement, executor.cfg)
            callStack.push(executor, currentStatement)
            newStmt(executor.cfg.stmts.first())
        }
        null
    } else {
        when (promiseState) {
            PromiseState.PENDING -> {
                error("Promise state should not be PENDING, but it is for $promise")
            }

            PromiseState.FULFILLED -> {
                // If the promise is already resolved, we can return this value.
                // val sort = typeToSort(expr.arg.type)
                val sort = typeToSort(EtsUnknownType)
                if (sort == unresolvedSort) {
                    val value = scope.calcOnState {
                        getResolvedValue(promise, addressSort)
                    }
                    check(value.isFakeObject())
                    value
                } else {
                    scope.calcOnState {
                        getResolvedValue(promise, sort)
                    }
                }
            }

            PromiseState.REJECTED -> {
                // If the promise is rejected, we throw an exception.
                val reason = scope.calcOnState {
                    getResolvedValue(promise, addressSort)
                }
                scope.doWithState {
                    // TODO: create proper exception
                    methodResult = TsMethodResult.TsException(reason, EtsStringType)
                }
                null
            }
        }
    }
}
