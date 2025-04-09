package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsValue
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
import org.usvm.memory.write
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsMethods
import org.usvm.utils.ensureSat

private val logger = KotlinLogging.logger {}

typealias TsStepScope = StepScope<TsState, EtsType, EtsStmt, TsContext>

class TsInterpreter(
    private val ctx: TsContext,
    private val graph: TsGraph,
) : UInterpreter<TsState>() {

    private val forkBlackList: UForkBlackList<TsState, EtsStmt> = UForkBlackList.createDefault()

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
            is EtsIfStmt -> visitIfStmt(scope, stmt)
            is EtsReturnStmt -> visitReturnStmt(scope, stmt)
            is EtsAssignStmt -> visitAssignStmt(scope, stmt)
            is EtsCallStmt -> visitCallStmt(scope, stmt)
            // is EtsThrowStmt -> visitThrowStmt(scope, stmt)
            is EtsNopStmt -> visitNopStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }

        return scope.stepResult()
    }

    private fun visitMethodCall(scope: TsStepScope, stmt: TsMethodCall) {
        exprResolverWithScope(scope)

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        when (stmt) {
            is TsVirtualMethodCallStmt -> {
                val methods = ctx.resolveEtsMethods(stmt.callee)
                if (methods.isEmpty()) {
                    logger.warn { "Could not resolve method: ${stmt.callee}" }
                    scope.assert(ctx.falseExpr)
                    return
                }

                logger.info {
                    "Preparing to fork on ${methods.size} methods: ${
                        methods.map { "${it.signature.enclosingClass.name}::${it.name}" }
                    }"
                }

                val conditionsWithBlocks: MutableList<Pair<UBoolExpr, TsState.() -> Unit>> = mutableListOf()

                for (method in methods) {
                    val block = { newState: TsState ->
                        val concreteCall = TsConcreteMethodCallStmt(
                            location = EtsStmtLocation.stub(scope.calcOnState { lastEnteredMethod }),
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
                    if (stmt.callee.name != "\$r") {
                        logger.warn { "No entry point for method ${stmt.callee}" }
                    }
                    return
                }

                scope.doWithState {
                    check(stmt.arguments.size == stmt.callee.parametersWithThisCount) {
                        "Expected ${stmt.callee.parameters.size}+1 arguments, got ${stmt.arguments.size}"
                    }
                    // TODO: push sorts for arguments
                    callStack.push(stmt.callee, stmt.returnSite)
                    memory.stack.push(stmt.arguments.toTypedArray(), stmt.callee.localsCount)
                    newStmt(entryPoint)
                }
            }
        }
    }

    private fun visitIfStmt(scope: TsStepScope, stmt: EtsIfStmt) {
        val exprResolver = exprResolverWithScope(scope)
        val expr = exprResolver.resolve(stmt.condition) ?: return

        val boolExpr = ctx.mkTruthyExpr(expr, scope)

        if (graph.successors(stmt).toList().size != 2) {
            logger.warn { "If statement has ${graph.successors(stmt).toList().size} successor(s)" }
            scope.assert(ctx.falseExpr)
            return
        }

        val (trueBranch, falseBranch) = graph.successors(stmt).take(2).toList()

        scope.forkWithBlackList(
            boolExpr,
            trueBranch,
            falseBranch,
            blockOnTrueState = { newStmt(trueBranch) },
            blockOnFalseState = { newStmt(falseBranch) },
        )
    }

    private fun visitReturnStmt(scope: TsStepScope, stmt: EtsReturnStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolve(it) ?: return }
            ?: ctx.mkUndefinedValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitAssignStmt(scope: TsStepScope, stmt: EtsAssignStmt) = with(ctx) {
        val exprResolver = exprResolverWithScope(scope)
        val rhvExpr = exprResolver.resolve(stmt.rhv) ?: return

        check(rhvExpr.sort != unresolvedSort) {
            "A value of the unresolved sort should never be returned from `resolve` function"
        }

        scope.doWithState {
            when (val lhv = stmt.lhv) {
                is EtsLocal -> {
                    val idx = mapLocalToIdx(lastEnteredMethod, lhv)
                    // Note: this can overwrite the sort from the previous assignment to the same local
                    saveSortForLocal(idx, rhvExpr.sort)

                    val lValue = mkRegisterStackLValue(rhvExpr.sort, idx)
                    memory.write(lValue, rhvExpr, guard = trueExpr)
                }

                is EtsArrayAccess -> {
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
                    //  In this case, it could be created manually: `EtsArrayType(EtsUnknownType, 1)`.
                    val lengthLValue = mkArrayLengthLValue(
                        ref = instance,
                        type = EtsArrayType(EtsUnknownType, 1)
                    ) // TODO: lhv.array.type as EtsArrayType
                    val currentLength = memory.read(lengthLValue)

                    val condition = mkBvSignedGreaterOrEqualExpr(bvIndex, currentLength)
                    val newLength = mkIte(condition, mkBvAddExpr(bvIndex, mkBv(1)), currentLength)

                    memory.write(lengthLValue, newLength, guard = trueExpr)

                    val fakeExpr = rhvExpr.toFakeObject(scope)

                    val lValue = mkArrayIndexLValue(
                        sort = addressSort,
                        ref = instance,
                        index = bvIndex.asExpr(sizeSort),
                        type = EtsArrayType(EtsUnknownType, 1), // TODO: lhv.array.type as EtsArrayType
                    )
                    memory.write(lValue, fakeExpr, guard = trueExpr)
                }

                is EtsInstanceFieldRef -> {
                    val instance = exprResolver.resolve(lhv.instance)?.asExpr(addressSort) ?: return@doWithState
                    // TODO: if the resolved expr is not address sort, we can just ignore this assignment,
                    //       since it is probably something like "(42).foo = 100" which is no-op (and not an error in js!).
                    //       Currently, `asExpr(addressSort)` will fail in such case.

                    val field = lhv.field
                    val fieldLValue = mkFieldLValue(addressSort, instance, field)

                    // Note: always write fake object
                    val fakeObject = rhvExpr.toFakeObject(scope)

                    // Write fake object to the field:
                    memory.write(fieldLValue, fakeObject, guard = trueExpr)

                    // Also write to original bool/fp fields the extracted "fake" bool/fp values:
                    val boolLValue = mkFieldLValue(boolSort, instance, field)
                    memory.write(boolLValue, fakeObject.extractBool(scope), guard = trueExpr)
                    val fpLValue = mkFieldLValue(fp64Sort, instance, field)
                    memory.write(fpLValue, fakeObject.extractFp(scope), guard = trueExpr)

                    let {}
                }

                is EtsStaticFieldRef -> {
                    val clazz = scene.projectAndSdkClasses.singleOrNull {
                        it.name == lhv.field.enclosingClass.name
                    } ?: run {
                        logger.warn {
                            "Could not uniquely resolve class: '${lhv.field.enclosingClass.name}'. Found ${
                                scene.projectAndSdkClasses.count { it.name == lhv.field.enclosingClass.name }
                            } classes: ${
                                scene.projectAndSdkClasses.filter { it.name == lhv.field.enclosingClass.name }
                            }"
                        }
                        scope.assert(falseExpr)
                        return@doWithState
                    }

                    val instance = scope.calcOnState { getStaticInstance(clazz) }

                    // TODO: initialize the static field first
                    //  Note: Since we are assigning to a static field, we can omit its initialization,
                    //        if it does not have any side effects.

                    val field = clazz.fields.single { it.name == lhv.field.name }

                    // Note: always write fake object
                    val fakeObject = rhvExpr.toFakeObject(scope)

                    // Write fake object to the field:
                    val fieldLValue = mkFieldLValue(addressSort, instance, field)
                    memory.write(fieldLValue, fakeObject, guard = trueExpr)

                    // Also write to original bool/fp fields the extracted "fake" bool/fp values:
                    val boolLValue = mkFieldLValue(boolSort, instance, field)
                    memory.write(boolLValue, fakeObject.extractBool(scope), guard = trueExpr)
                    val fpLValue = mkFieldLValue(fp64Sort, instance, field)
                    memory.write(fpLValue, fakeObject.extractFp(scope), guard = trueExpr)

                    // Take into account the type of the field:
                    val fakeType = fakeObject.getFakeType(scope)
                    val sort = typeToSort(field.type)
                    when (sort) {
                        unresolvedSort -> {
                            // Do nothing
                        }

                        boolSort -> {
                            scope.assert(fakeType.boolTypeExpr)
                        }

                        fp64Sort -> {
                            scope.assert(fakeType.fpTypeExpr)
                        }

                        addressSort -> {
                            scope.assert(fakeType.refTypeExpr)
                        }

                        else -> {
                            error("Unsupported sort: $sort")
                        }
                    }
                }

                else -> TODO("Not yet implemented")
            }

            val nextStmt = stmt.nextStmt ?: return@doWithState
            newStmt(nextStmt)
        }
    }

    private fun visitCallStmt(scope: TsStepScope, stmt: EtsCallStmt) {
        val exprResolver = exprResolverWithScope(scope)

        exprResolver.resolve(stmt.expr) ?: return

        scope.doWithState {
            val nextStmt = stmt.nextStmt ?: return@doWithState
            newStmt(nextStmt)
        }
    }

    private fun visitNopStmt(scope: TsStepScope, stmt: EtsNopStmt) {
        // Do nothing
    }

    private fun exprResolverWithScope(scope: TsStepScope): TsExprResolver =
        TsExprResolver(ctx, scope, ::mapLocalToIdx)

    // (method, localName) -> idx
    private val localVarToIdx: MutableMap<EtsMethod, Map<String, Int>> = hashMapOf()

    private fun mapLocalToIdx(method: EtsMethod, local: EtsValue): Int =
        // Note: below, 'n' means the number of arguments
        when (local) {
            // Note: locals have indices starting from (n+1)
            is EtsLocal -> {
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
            is EtsThis -> method.parameters.size

            // Note: arguments have indices from 0 to (n-1)
            is EtsParameterRef -> local.index

            else -> error("Unexpected local: $local")
        }

    fun getInitialState(method: EtsMethod, targets: List<TsTarget>): TsState {
        val state = TsState(
            ctx = ctx,
            ownership = MutabilityOwnership(),
            entrypoint = method,
            targets = UTargetsSet.from(targets),
        )

        val solver = ctx.solver<EtsType>()

        // TODO check for statics
        val thisLValue = mkRegisterStackLValue(ctx.addressSort, method.parameters.size)
        val thisRef = state.memory.read(thisLValue)

        state.pathConstraints += with(ctx) {
            mkAnd(
                mkNot(mkHeapRefEq(thisRef, mkTsNullValue())),
                mkNot(mkHeapRefEq(thisRef, mkUndefinedValue())),
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

        state.memory.types.allocate(ctx.mkTsNullValue().address, EtsNullType)

        return state
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt: EtsStmt?
        get() = graph.successors(this).firstOrNull()
}
