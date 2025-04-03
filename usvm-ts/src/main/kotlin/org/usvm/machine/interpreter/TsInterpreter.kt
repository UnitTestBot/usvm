package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
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
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsFields
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
        val expr = exprResolver.resolve(stmt.rhv) ?: return

        check(expr.sort != unresolvedSort) {
            "A value of the unresolved sort should never be returned from `resolve` function"
        }

        scope.doWithState {
            when (val lhv = stmt.lhv) {
                is EtsLocal -> {
                    val idx = mapLocalToIdx(lastEnteredMethod, lhv)
                    saveSortForLocal(idx, expr.sort)

                    val lValue = mkRegisterStackLValue(expr.sort, idx)
                    memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
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

                    val fakeExpr = expr.toFakeObject(scope)

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
                    val etsFields = resolveEtsFields(
                        lhv.instance,
                        EtsFieldSignature(EtsClassSignature.UNKNOWN, lhv.field.name, EtsUnknownType)
                    )
                    val etsFieldType = etsFields
                        .filter { it.declaringClass != null && it.declaringClass!!.category != EtsClassCategory.ENUM }
                        .map { it.type }.distinct().singleOrNull() ?: EtsUnknownType
                    val field = EtsFieldSignature(EtsClassSignature.UNKNOWN, lhv.field.name, EtsUnknownType)
                    val sort = typeToSort(etsFieldType)
                    if (sort == unresolvedSort) {
                        val fakeObject = expr.toFakeObject(scope)
                        val lValue = mkFieldLValue(
                            sort = addressSort,
                            ref = instance,
                            field = field,
                        )
                        memory.write(lValue, fakeObject, guard = trueExpr)

                        // TODO: write to original fields
                        // val fpLValue = mkFieldLValue(
                        //     sort = fp64Sort,
                        //     ref = instance,
                        //     field = field,
                        // )
                        // memory.write(fpLValue, fakeObject.extractFp(scope), guard = trueExpr)
                        // val boolLValue = mkFieldLValue(
                        //     sort = boolSort,
                        //     ref = instance,
                        //     field = field,
                        // )
                        // memory.write(boolLValue, fakeObject.extractBool(scope), guard = trueExpr)
                    } else {
                        if (expr.isFakeObject()) {
                            val lValue = mkFieldLValue(
                                sort = sort,
                                ref = instance,
                                field = field,
                            )
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
                                error("Unsupported sort: $sort")
                            }
                        } else {
                            val lValue = mkFieldLValue(
                                sort = sort,
                                ref = instance,
                                field = field,
                            )
                            memory.write(lValue, expr.asExpr(sort), guard = trueExpr)

                            // Note: here, we could simply write the value to the field,
                            //       but we nevertheless wrap it into a fake type and add the corresponding
                            //       type constraint in order to make correct the "unresolved readings" that
                            //       create the fake object itself (yet, do not know about the type constraints).

                            // val fakeObject = expr.toFakeObject(scope)
                            // val lValue = mkFieldLValue(
                            //     sort = addressSort,
                            //     ref = instance,
                            //     field = field,
                            // )
                            // memory.write(lValue, fakeObject, guard = trueExpr)
                            // TODO: fake type constraints...

                            // TODO: write to original fields
                            // val fpLValue = mkFieldLValue(
                            //     sort = fp64Sort,
                            //     ref = instance,
                            //     field = field,
                            // )
                            // memory.write(fpLValue, fakeObject.extractFp(scope), guard = trueExpr)
                            // val boolLValue = mkFieldLValue(
                            //     sort = boolSort,
                            //     ref = instance,
                            //     field = field,
                            // )
                            // memory.write(boolLValue, fakeObject.extractBool(scope), guard = trueExpr)
                        }
                    }
                }

                is EtsStaticFieldRef -> {
                    val clazz = scene.projectAndSdkClasses.singleOrNull {
                        it.name == lhv.field.enclosingClass.name
                    } ?: return@doWithState

                    val instance = scope.calcOnState { getStaticInstance(clazz) }

                    // TODO: initialize the static field first
                    //  Note: Since we are assigning to a static field, we can omit its initialization,
                    //        if it does not have any side effects.

                    val field = clazz.fields.single { it.name == lhv.field.name }
                    val sort = typeToSort(field.type)
                    if (sort == unresolvedSort) {
                        val fakeObject = expr.toFakeObject(scope)
                        val lValue = mkFieldLValue(addressSort, instance, field.signature)
                        memory.write(lValue, fakeObject, guard = trueExpr)
                    } else {
                        // val lValue = mkFieldLValue(sort, instance, field.signature)
                        // memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)

                        val fakeObject = expr.toFakeObject(scope)
                        val fakeType = fakeObject.getFakeType(scope)
                        val lValue = mkFieldLValue(
                            sort = addressSort,
                            ref = instance,
                            field = field.signature,
                        )
                        memory.write(lValue, fakeObject, guard = trueExpr)
                        when (sort) {
                            boolSort -> {
                                scope.assert(fakeType.boolTypeExpr)
                            }

                            fp64Sort -> {
                                scope.assert(fakeType.fpTypeExpr)
                            }

                            addressSort -> {
                                scope.assert(fakeType.refTypeExpr)
                            }

                            else -> error("Unsupported sort: $sort")
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

        exprResolver.resolve(stmt.expr) ?: run {
            logger.warn { "Could not resolve call expression: ${stmt.expr}" }
            return
        }

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

        state.memory.types.allocate(ctx.mkTsNullValue().address, EtsNullType)

        return state
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt: EtsStmt?
        get() = graph.successors(this).firstOrNull()
}
