package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.api.targets.TsTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TsApplicationGraph
import org.usvm.machine.TsContext
import org.usvm.machine.expr.TsExprResolver
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
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
import org.usvm.utils.ensureSat

private val logger = KotlinLogging.logger {}

typealias TsStepScope = StepScope<TsState, EtsType, EtsStmt, TsContext>

@Suppress("UNUSED_PARAMETER")
class TsInterpreter(
    private val ctx: TsContext,
    private val applicationGraph: TsApplicationGraph,
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
            is EtsIfStmt -> visitIfStmt(scope, stmt)
            is EtsReturnStmt -> visitReturnStmt(scope, stmt)
            is EtsAssignStmt -> visitAssignStmt(scope, stmt)
            is EtsCallStmt -> visitCallStmt(scope, stmt)
            is EtsThrowStmt -> visitThrowStmt(scope, stmt)
            is EtsGotoStmt -> visitGotoStmt(scope, stmt)
            is EtsNopStmt -> visitNopStmt(scope, stmt)
            is EtsSwitchStmt -> visitSwitchStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }

        return scope.stepResult()
    }

    private fun visitIfStmt(scope: TsStepScope, stmt: EtsIfStmt) {
        val exprResolver = exprResolverWithScope(scope)
        val expr = exprResolver.resolve(stmt.condition) ?: return

        val boolExpr = if (expr.sort == ctx.boolSort) {
            expr.asExpr(ctx.boolSort)
        } else {
            ctx.mkTruthyExpr(expr, scope)
        }

        val (negStmt, posStmt) = applicationGraph.successors(stmt).take(2).toList()

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
                        isSigned = true
                    ).asExpr(sizeSort)

                    val lengthLValue = mkArrayLengthLValue(instance, lhv.array.type as EtsArrayType)
                    val currentLength = memory.read(lengthLValue).asExpr(sizeSort)

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

                    val sort = typeToSort(lhv.type)
                    if (sort == unresolvedSort) {
                        val fakeObject = expr.toFakeObject(scope)
                        val lValue = mkFieldLValue(addressSort, instance, lhv.field)
                        memory.write(lValue, fakeObject, guard = trueExpr)
                    } else {
                        val lValue = mkFieldLValue(sort, instance, lhv.field)
                        memory.write(lValue, expr.asExpr(sort), guard = trueExpr)
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

    private fun visitThrowStmt(scope: TsStepScope, stmt: EtsThrowStmt) {
        // TODO do not forget to pop the sorts call stack in the state
        TODO()
    }

    private fun visitGotoStmt(scope: TsStepScope, stmt: EtsGotoStmt) {
        TODO()
    }

    private fun visitNopStmt(scope: TsStepScope, stmt: EtsNopStmt) {
        TODO()
    }

    private fun visitSwitchStmt(scope: TsStepScope, stmt: EtsSwitchStmt) {
        TODO()
    }

    private fun exprResolverWithScope(scope: TsStepScope): TsExprResolver =
        TsExprResolver(ctx, scope, ::mapLocalToIdx)

    // (method, localName) -> idx
    private val localVarToIdx: MutableMap<EtsMethod, MutableMap<String, Int>> = hashMapOf()

    private fun mapLocalToIdx(method: EtsMethod, local: EtsValue): Int =
        // Note: below, 'n' means the number of arguments
        when (local) {
            // Note: locals have indices starting from (n+1)
            is EtsLocal -> localVarToIdx
                .getOrPut(method) { hashMapOf() }
                .let {
                    it.getOrPut(local.name) { method.parametersWithThisCount + it.size }
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

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt: EtsStmt?
        get() = applicationGraph.successors(this).firstOrNull()
}
