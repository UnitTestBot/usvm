package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UInterpreter
import org.usvm.api.targets.TsTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.TsGraph
import org.usvm.machine.TsMethodCall
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.expr.TsExprResolver
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.allLocals
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.model.TsArrayAccess
import org.usvm.model.TsArrayType
import org.usvm.model.TsAssignStmt
import org.usvm.model.TsCallStmt
import org.usvm.model.TsClassSignature
import org.usvm.model.TsFieldSignature
import org.usvm.model.TsIfStmt
import org.usvm.model.TsInstLocation
import org.usvm.model.TsInstanceFieldRef
import org.usvm.model.TsLocal
import org.usvm.model.TsMethod
import org.usvm.model.TsNopStmt
import org.usvm.model.TsNullType
import org.usvm.model.TsParameterRef
import org.usvm.model.TsReturnStmt
import org.usvm.model.TsStaticFieldRef
import org.usvm.model.TsStmt
import org.usvm.model.TsThis
import org.usvm.model.TsType
import org.usvm.model.TsUnknownType
import org.usvm.model.TsValue
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveTsFields
import org.usvm.util.resolveTsMethods
import org.usvm.utils.ensureSat

private val logger = KotlinLogging.logger {}

typealias TsStepScope = StepScope<TsState, TsType, TsStmt, TsContext>

class TsInterpreter(
    private val ctx: TsContext,
    private val graph: TsGraph,
) : UInterpreter<TsState>() {

    private val forkBlackList: UForkBlackList<TsState, TsStmt> = UForkBlackList.createDefault()

    override fun step(state: TsState): StepResult<TsState> {
        val stmt = state.lastStmt
        val scope = StepScope(state, forkBlackList)

        val result = state.methodResult
        if (result is TsMethodResult.TsException) {
            // TODO catch processing
            scope.doWithState {
                val returnSite = callStack.pop()

                if (callStack.isNotEmpty()) {
                    memory.stack.pop()
                }

                if (returnSite != null) {
                    newStmt(returnSite)
                }
            }

            return scope.stepResult()
        }

        when (stmt) {
            is TsMethodCall -> visitMethodCall(scope, stmt)
            is TsIfStmt -> visitIfStmt(scope, stmt)
            is TsReturnStmt -> visitReturnStmt(scope, stmt)
            is TsAssignStmt -> visitAssignStmt(scope, stmt)
            is TsCallStmt -> visitCallStmt(scope, stmt)
            is TsNopStmt -> visitNopStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }

        return scope.stepResult()
    }

    private fun visitMethodCall(scope: TsStepScope, stmt: TsMethodCall) {
        val exprResolver = exprResolverWithScope(scope)

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        when (stmt) {
            is TsVirtualMethodCallStmt -> {
                val methods = ctx.resolveTsMethods(stmt.callee)
                if (methods.isEmpty()) {
                    scope.assert(ctx.falseExpr)
                    return
                }

                val conditionsWithBlocks: MutableList<Pair<UBoolExpr, TsState.() -> Unit>> = mutableListOf()

                for (method in methods) {
                    val block = { newState: TsState ->
                        val concreteCall = TsConcreteMethodCallStmt(
                            location = TsInstLocation(scope.calcOnState { lastEnteredMethod }, -1),
                            callee = method,
                            arguments = stmt.arguments,
                            returnSite = stmt.returnSite,
                        )
                        newState.newStmt(concreteCall)
                    }
                    conditionsWithBlocks += ctx.trueExpr to block
                }

                // TODO: fork on multiple methods
                scope.forkMulti(conditionsWithBlocks)
            }

            is TsConcreteMethodCallStmt -> {
                // TODO: observer
                // TODO: native/abstract methods without entrypoints

                val entryPoint = graph.entryPoints(stmt.callee).singleOrNull()

                if (entryPoint == null) {
                    logger.warn { "No entry point for method ${stmt.callee}" }
                    return
                }

                scope.doWithState {
                    check(stmt.arguments.size == stmt.callee.parametersWithThisCount) {
                        "Arguments size should be equal to the method parameters size"
                    }
                    // TODO: push sorts for arguments
                    callStack.push(stmt.callee, stmt.returnSite)
                    memory.stack.push(stmt.arguments.toTypedArray(), stmt.callee.localsCount)
                    newStmt(entryPoint)
                }
            }
        }
    }

    private fun visitIfStmt(scope: TsStepScope, stmt: TsIfStmt) {
        val exprResolver = exprResolverWithScope(scope)
        val expr = exprResolver.resolve(stmt.condition) ?: return

        val boolExpr = if (expr.sort == ctx.boolSort) {
            expr.asExpr(ctx.boolSort)
        } else {
            ctx.mkTruthyExpr(expr, scope)
        }

        val (negStmt, posStmt) = graph.successors(stmt).take(2).toList()

        scope.forkWithBlackList(
            boolExpr,
            posStmt,
            negStmt,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) },
        )
    }

    private fun visitReturnStmt(scope: TsStepScope, stmt: TsReturnStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolve(it) ?: return }
            ?: ctx.mkUndefinedValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitAssignStmt(scope: TsStepScope, stmt: TsAssignStmt) = with(ctx) {
        val exprResolver = exprResolverWithScope(scope)
        val expr = exprResolver.resolve(stmt.rhv) ?: return

        check(expr.sort != unresolvedSort) {
            "A value of the unresolved sort should never be returned from `resolve` function"
        }

        scope.doWithState {
            when (val lhv = stmt.lhv) {
                is TsLocal -> {
                    val idx = mapLocalToIdx(lastEnteredMethod, lhv)
                    saveSortForLocal(idx, expr.sort)

                    val lValue = mkRegisterStackLValue(expr.sort, idx)
                    memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
                }

                is TsArrayAccess -> {
                    val instance = exprResolver.resolve(lhv.array)?.asExpr(addressSort) ?: return@doWithState
                    val index = exprResolver.resolve(lhv.index)?.asExpr(fp64Sort) ?: return@doWithState

                    // TODO fork on floating point field
                    val bvIndex = mkFpToBvExpr(
                        roundingMode = fpRoundingModeSortDefaultValue(),
                        value = index,
                        bvSize = 32,
                        isSigned = true,
                    ).asExpr(sizeSort)

                    // TODO: handle the case when `lhv.array.type` is NOT an array.
                    //  In this case, it could be created manually: `TsArrayType(TsUnknownType, 1)`.
                    val lengthLValue = mkArrayLengthLValue(
                        ref = instance,
                        type = TsArrayType(TsUnknownType, 1)
                    ) // TODO: lhv.array.type as TsArrayType
                    val currentLength = memory.read(lengthLValue)

                    val condition = mkBvSignedGreaterOrEqualExpr(bvIndex, currentLength)
                    val newLength = mkIte(condition, mkBvAddExpr(bvIndex, mkBv(1)), currentLength)

                    memory.write(lengthLValue, newLength, guard = trueExpr)

                    val fakeExpr = expr.toFakeObject(scope)

                    val lValue = mkArrayIndexLValue(
                        sort = addressSort,
                        ref = instance,
                        index = bvIndex.asExpr(sizeSort),
                        type = TsArrayType(TsUnknownType, 1), // TODO: lhv.array.type as TsArrayType
                    )
                    memory.write(lValue, fakeExpr, guard = trueExpr)
                }

                is TsInstanceFieldRef -> {
                    val instance = exprResolver.resolve(lhv.instance)?.asExpr(addressSort) ?: return@doWithState
                    val etsFields = resolveTsFields(
                        lhv.instance,
                        TsFieldSignature(TsClassSignature.UNKNOWN, lhv.fieldName, TsUnknownType)
                    )
                    val etsFieldType = etsFields.map { it.type }.distinct().singleOrNull() ?: TsUnknownType
                    val sort = typeToSort(etsFieldType)
                    if (sort == unresolvedSort) {
                        val fakeObject = expr.toFakeObject(scope)
                        val lValue = mkFieldLValue(
                            sort = addressSort,
                            ref = instance,
                            field = TsFieldSignature(TsClassSignature.UNKNOWN, lhv.fieldName, TsUnknownType)
                        )
                        memory.write(lValue, fakeObject, guard = trueExpr)
                    } else {
                        val lValue = mkFieldLValue(
                            sort = sort,
                            ref = instance,
                            field = TsFieldSignature(TsClassSignature.UNKNOWN, lhv.fieldName, TsUnknownType)
                        )
                        if (expr.isFakeObject()) {
                            val fakeType = expr.getFakeType(scope)
                            if (sort == boolSort) {
                                scope.assert(fakeType.boolTypeExpr)
                                memory.write(lValue, expr.extractBool(scope).asExpr(sort), guard = trueExpr)
                            } else if (sort == fp64Sort) {
                                scope.assert(fakeType.fpTypeExpr)
                                memory.write(lValue, expr.extractFp(scope).asExpr(sort), guard = trueExpr)
                            } else if (sort == addressSort) {
                                scope.assert(fakeType.refTypeExpr)
                                memory.write(lValue, expr.extractRef(scope).asExpr(sort), guard = trueExpr)
                            } else {
                                let {}
                            }
                        } else {
                            memory.write(lValue, expr.asExpr(sort), guard = trueExpr)
                        }
                    }
                }

                is TsStaticFieldRef -> {
                    val clazz = scene.projectAndSdkClasses.singleOrNull {
                        it.name == lhv.enclosingClass.typeName
                    } ?: return@doWithState

                    val instance = scope.calcOnState { getStaticInstance(clazz) }

                    // TODO: initialize the static field first
                    //  Note: Since we are assigning to a static field, we can omit its initialization,
                    //        if it does not have any side effects.

                    val field = clazz.fields.single { it.name == lhv.fieldName }
                    val sort = typeToSort(field.type)
                    if (sort == unresolvedSort) {
                        val lValue = mkFieldLValue(addressSort, instance, field.signature)
                        val fakeObject = expr.toFakeObject(scope)
                        memory.write(lValue, fakeObject, guard = trueExpr)
                    } else {
                        val lValue = mkFieldLValue(sort, instance, field.signature)
                        memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
                    }
                }

                else -> TODO("Not yet implemented")
            }

            val nextStmt = stmt.nextStmt ?: return@doWithState
            newStmt(nextStmt)
        }
    }

    private fun visitCallStmt(scope: TsStepScope, stmt: TsCallStmt) {
        val exprResolver = exprResolverWithScope(scope)

        exprResolver.resolve(stmt.expr) ?: return

        scope.doWithState {
            val nextStmt = stmt.nextStmt ?: return@doWithState
            newStmt(nextStmt)
        }
    }

    private fun visitNopStmt(scope: TsStepScope, stmt: TsNopStmt) {
        // Do nothing
    }

    private fun exprResolverWithScope(scope: TsStepScope): TsExprResolver =
        TsExprResolver(ctx, scope, ::mapLocalToIdx)

    // (method, localName) -> idx
    private val localVarToIdx: MutableMap<TsMethod, Map<String, Int>> = hashMapOf()

    private fun mapLocalToIdx(method: TsMethod, local: TsValue): Int =
        // Note: below, 'n' means the number of arguments
        when (local) {
            // Note: locals have indices starting from (n+1)
            is TsLocal -> {
                val map = localVarToIdx.getOrPut(method) {
                    // TODO: replace 'allLocals' with 'getDeclaredLocals()'
                    method.allLocals.mapIndexed { i, local ->
                        val idx = i + method.parametersWithThisCount
                        local.name to idx
                    }.toMap().also {
                        check(it.size == method.localsCount)
                    }
                }
                map[local.name] ?: error("Local not declared: $local")
            }

            // Note: 'this' has index 'n'
            is TsThis -> method.parameters.size

            // Note: arguments have indices from 0 to (n-1)
            is TsParameterRef -> local.index

            else -> error("Unexpected local: $local")
        }

    fun getInitialState(method: TsMethod, targets: List<TsTarget>): TsState {
        val state = TsState(
            ctx = ctx,
            ownership = MutabilityOwnership(),
            entrypoint = method,
            targets = UTargetsSet.from(targets),
        )

        val solver = ctx.solver<TsType>()

        // TODO check for statics
        val thisInstanceRef = mkRegisterStackLValue(ctx.addressSort, method.parameters.count())
        val thisRef = state.memory.read(thisInstanceRef).asExpr(ctx.addressSort)

        state.pathConstraints += with(ctx) {
            mkNot(
                mkOr(
                    ctx.mkHeapRefEq(thisRef, ctx.mkTsNullValue()),
                    ctx.mkHeapRefEq(thisRef, ctx.mkUndefinedValue())
                )
            )
        }

        // TODO fix incorrect type streams
        // val thisTypeConstraint = state.memory.types.evalTypeEquals(thisRef, TsClassType(method.enclosingClass))
        // state.pathConstraints += thisTypeConstraint

        val model = solver.check(state.pathConstraints).ensureSat().model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parametersWithThisCount, method.localsCount)
        state.newStmt(method.cfg.stmts.first())

        state.memory.types.allocate(ctx.mkTsNullValue().address, TsNullType)

        return state
    }

    // TODO: expand with interpreter implementation
    private val TsStmt.nextStmt: TsStmt?
        get() = graph.successors(this).firstOrNull()
}
