package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.UInterpreter
import org.usvm.api.allocateArrayInitialized
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.api.targets.TsTarget
import org.usvm.api.typeStreamOf
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.TsGraph
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsOptions
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.expr.TsExprResolver
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.types.single
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsField
import org.usvm.util.resolveEtsMethods
import org.usvm.utils.ensureSat

private val logger = KotlinLogging.logger {}

typealias TsStepScope = StepScope<TsState, EtsType, EtsStmt, TsContext>

@Suppress("UNUSED_PARAMETER")
class TsInterpreter(
    private val ctx: TsContext,
    private val graph: TsGraph,
    private val tsOptions: TsOptions,
    private val observer: TsInterpreterObserver? = null,
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

        // TODO: rewrite the main loop (step) as follows:
        //  check methodResult
        //  if success, reset the call (NoCall), return the result
        //  if exception, see above
        //  if no call, visit

        when (stmt) {
            is TsVirtualMethodCallStmt -> visitVirtualMethodCall(scope, stmt)
            is TsConcreteMethodCallStmt -> visitConcreteMethodCall(scope, stmt)
            is EtsIfStmt -> visitIfStmt(scope, stmt)
            is EtsReturnStmt -> visitReturnStmt(scope, stmt)
            is EtsAssignStmt -> visitAssignStmt(scope, stmt)
            is EtsCallStmt -> visitCallStmt(scope, stmt)
            is EtsThrowStmt -> visitThrowStmt(scope, stmt)
            is EtsNopStmt -> visitNopStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }

        return scope.stepResult()
    }

    private fun visitVirtualMethodCall(scope: TsStepScope, stmt: TsVirtualMethodCallStmt) {

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        val instance = stmt.instance
        checkNotNull(instance)
        val concreteRef = scope.calcOnState { models.first().eval(instance) }

        val concreteMethods: MutableList<EtsMethod> = mutableListOf()

        if (isAllocatedConcreteHeapRef(concreteRef)) {
            val type = scope.calcOnState { memory.typeStreamOf(concreteRef) }.single()
            if (type is EtsClassType) {
                // TODO: handle non-unique classes
                val cls = ctx.scene.projectAndSdkClasses.first { it.name == type.typeName }
                val method = cls.methods.first { it.name == stmt.callee.name }
                concreteMethods += method
            } else {
                logger.warn {
                    "Could not resolve method: ${stmt.callee} on type: $type"
                }
                scope.assert(ctx.falseExpr)
                return
            }
        } else {
            val methods = ctx.resolveEtsMethods(stmt.callee)
            if (methods.isEmpty()) {
                logger.warn { "Could not resolve method: ${stmt.callee}" }
                scope.assert(ctx.falseExpr)
                return
            }
            concreteMethods += methods
        }

        logger.info {
            "Preparing to fork on ${concreteMethods.size} methods: ${
                concreteMethods.map { "${it.signature.enclosingClass.name}::${it.name}" }
            }"
        }
        val conditionsWithBlocks = concreteMethods.map { method ->
            val concreteCall = stmt.toConcrete(method)
            val block = { state: TsState -> state.newStmt(concreteCall) }
            ctx.trueExpr to block
        }
        scope.forkMulti(conditionsWithBlocks)
    }

    private fun visitConcreteMethodCall(scope: TsStepScope, stmt: TsConcreteMethodCallStmt) {

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        // TODO: observer
        // TODO: native/abstract methods without entrypoints

        val entryPoint = graph.entryPoints(stmt.callee).singleOrNull()

        if (entryPoint == null) {
            logger.warn { "No entry point for method: ${stmt.callee}" }
            return
        }

        scope.doWithState {
            val args = mutableListOf<UExpr<*>>()
            val numActual = stmt.args.size
            val numFormal = stmt.callee.parameters.size

            // vararg call:
            // function f(x: any, ...args: any[]) {}
            //   f(1, 2, 3) -> f(1, [2, 3])
            //   f(1, 2) -> f(1, [2])
            //   f(1) -> f(1, [])
            //   f() -> f(undefined, [])

            // normal call:
            // function g(x: any, y: any) {}
            //   g(1, 2) -> g(1, 2)
            //   g(1) -> g(1, undefined)
            //   g() -> g(undefined, undefined)
            //   g(1, 2, 3) -> g(1, 2)

            if (stmt.callee.parameters.isNotEmpty() && stmt.callee.parameters.last().isRest) {
                // vararg call

                // first n-1 args are normal
                val numNormal = numFormal - 1
                args += stmt.args.take(numNormal)

                // fill missing normal args with undefined
                repeat(numNormal - numActual) {
                    args += ctx.mkUndefinedValue()
                }

                // wrap rest args in array
                val content = stmt.args.drop(numNormal).map {
                    with(ctx) { it.toFakeObject(scope) }
                }
                val array = scope.calcOnState {
                    val type = EtsArrayType(EtsUnknownType, 1)
                    memory.allocateArrayInitialized(
                        type = ctx.arrayDescriptorOf(type),
                        sort = ctx.addressSort,
                        sizeSort = ctx.sizeSort,
                        contents = content.asSequence(),
                    )
                }
                args += array
            } else {
                // normal call

                // ignore extra args
                args += stmt.args.take(numFormal)

                // fill missing args with undefined
                repeat(numFormal - numActual) {
                    args += ctx.mkUndefinedValue()
                }
            }

            check(args.size == numFormal) {
                "Expected ${numFormal} arguments, got ${args.size}"
            }

            args += stmt.instance!!

            // TODO: re-check push sorts for arguments
            pushSortsForActualArguments(args)
            callStack.push(stmt.callee, stmt.returnSite)
            memory.stack.push(args.toTypedArray(), stmt.callee.localsCount)
            newStmt(entryPoint)
        }
    }

    private fun visitIfStmt(scope: TsStepScope, stmt: EtsIfStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val simpleValueResolver = exprResolver.simpleValueResolver
        observer?.onIfStatement(simpleValueResolver, stmt, scope)

        val expr = exprResolver.resolve(stmt.condition) ?: return

        val boolExpr = if (expr.sort == ctx.boolSort) {
            expr.asExpr(ctx.boolSort)
        } else {
            ctx.mkTruthyExpr(expr, scope)
        }

        observer?.onIfStatementWithResolvedCondition(simpleValueResolver, stmt, boolExpr, scope)

        val (posStmt, negStmt) = graph.successors(stmt).take(2).toList()

        scope.forkWithBlackList(
            boolExpr,
            posStmt,
            negStmt,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) },
        )
    }

    private fun visitReturnStmt(scope: TsStepScope, stmt: EtsReturnStmt) {
        val exprResolver = exprResolverWithScope(scope)

        observer?.onReturnStatement(exprResolver.simpleValueResolver, stmt, scope)

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolve(it) ?: return }
            ?: ctx.mkUndefinedValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitAssignStmt(scope: TsStepScope, stmt: EtsAssignStmt) = with(ctx) {
        val exprResolver = exprResolverWithScope(scope)

        stmt.callExpr?.let {
            val methodResult = scope.calcOnState { methodResult }

            when (methodResult) {
                is TsMethodResult.NoCall -> observer?.onCallWithUnresolvedArguments(
                    exprResolver.simpleValueResolver,
                    it,
                    scope
                )

                is TsMethodResult.Success -> observer?.onAssignStatement(exprResolver.simpleValueResolver, stmt, scope)
                is TsMethodResult.TsException -> error("Exceptions must be processed earlier")
            }

            if (!tsOptions.interproceduralAnalysis && methodResult == TsMethodResult.NoCall) {
                mockMethodCall(scope, it.callee)
                return
            }
        } ?: observer?.onAssignStatement(exprResolver.simpleValueResolver, stmt, scope)

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
                        isSigned = true
                    ).asExpr(sizeSort)

                    // TODO: handle the case when `lhv.array.type` is NOT an array.
                    //  In this case, it could be created manually: `EtsArrayType(EtsUnknownType, 1)`.
                    val lengthLValue = mkArrayLengthLValue(instance, lhv.array.type as EtsArrayType)
                    val currentLength = memory.read(lengthLValue)

                    val condition = mkBvSignedGreaterOrEqualExpr(bvIndex, currentLength)
                    val newLength = mkIte(condition, mkBvAddExpr(bvIndex, mkBv(1)), currentLength)

                    memory.write(lengthLValue, newLength, guard = trueExpr)

                    val fakeExpr = expr.toFakeObject(scope)

                    val lValue = mkArrayIndexLValue(
                        addressSort,
                        instance,
                        bvIndex.asExpr(sizeSort),
                        lhv.array.type as EtsArrayType
                    )
                    memory.write(lValue, fakeExpr, guard = trueExpr)
                }

                is EtsInstanceFieldRef -> {
                    val instance = exprResolver.resolve(lhv.instance)?.asExpr(addressSort) ?: return@doWithState
                    val etsField = resolveEtsField(lhv.instance, lhv.field)
                    val sort = typeToSort(etsField.type)
                    if (sort == unresolvedSort) {
                        val fakeObject = expr.toFakeObject(scope)
                        val lValue = mkFieldLValue(addressSort, instance, lhv.field)
                        memory.write(lValue, fakeObject, guard = trueExpr)
                    } else {
                        val lValue = mkFieldLValue(sort, instance, lhv.field)
                        memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
                    }
                }

                is EtsStaticFieldRef -> {
                    val clazz = scene.projectAndSdkClasses.singleOrNull {
                        it.signature == lhv.field.enclosingClass
                    } ?: return@doWithState

                    val instance = scope.calcOnState { getStaticInstance(clazz) }

                    // TODO: initialize the static field first
                    //  Note: Since we are assigning to a static field, we can omit its initialization,
                    //        if it does not have any side effects.

                    val field = clazz.fields.single { it.name == lhv.field.name }
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

    private fun visitCallStmt(scope: TsStepScope, stmt: EtsCallStmt) {
        if (scope.calcOnState { methodResult } is TsMethodResult.Success) {
            scope.doWithState {
                methodResult = TsMethodResult.NoCall
                newStmt(stmt.nextStmt!!)
            }
            return
        }

        if (tsOptions.interproceduralAnalysis) {
            val exprResolver = exprResolverWithScope(scope)
            exprResolver.resolve(stmt.expr) ?: return
            val nextStmt = stmt.nextStmt ?: return
            scope.doWithState { newStmt(nextStmt) }
            return
        }

        // intraprocedural analysis
        mockMethodCall(scope, stmt.method.signature)
    }

    private fun visitThrowStmt(scope: TsStepScope, stmt: EtsThrowStmt) {
        // TODO do not forget to pop the sorts call stack in the state
        val exprResolver = exprResolverWithScope(scope)
        observer?.onThrowStatement(exprResolver.simpleValueResolver, stmt, scope)
        TODO()
    }

    private fun visitNopStmt(scope: TsStepScope, stmt: EtsNopStmt) {
        TODO()
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
                    method.getDeclaredLocals().mapIndexed { idx, local ->
                        val localIdx = idx + method.parametersWithThisCount
                        local.name to localIdx
                    }.toMap()
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
        // val thisTypeConstraint = state.memory.types.evalTypeEquals(thisRef, EtsClassType(method.enclosingClass))
        // state.pathConstraints += thisTypeConstraint

        val model = solver.check(state.pathConstraints).ensureSat().model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parametersWithThisCount, method.localsCount)
        state.newStmt(method.cfg.instructions.first())

        state.memory.types.allocate(ctx.mkTsNullValue().address, EtsNullType)

        return state
    }

    private fun mockMethodCall(scope: TsStepScope, method: EtsMethodSignature) {
        scope.doWithState {
            with(ctx) {
                val resultSort = typeToSort(method.returnType)
                val resultValue = when (resultSort) {
                    is UAddressSort -> makeSymbolicRefUntyped()
                    is TsUnresolvedSort -> {
                        mkFakeValue(
                            scope,
                            makeSymbolicPrimitive(ctx.boolSort),
                            makeSymbolicPrimitive(ctx.fp64Sort),
                            makeSymbolicRefUntyped()
                        )
                    }

                    else -> makeSymbolicPrimitive(resultSort)
                }
                methodResult = TsMethodResult.Success.MockedCall(method, resultValue)
            }
        }
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt: EtsStmt?
        get() = graph.successors(this).firstOrNull()
}
