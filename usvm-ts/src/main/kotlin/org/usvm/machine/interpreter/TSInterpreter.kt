package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsCallStmt
import org.jacodb.ets.base.EtsGotoStmt
import org.jacodb.ets.base.EtsIfStmt
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsSwitchStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsThrowStmt
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethod
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.USort
import org.usvm.api.targets.TSTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.TSApplicationGraph
import org.usvm.machine.TSContext
import org.usvm.machine.expr.TSExprResolver
import org.usvm.machine.expr.TSExprTransformer
import org.usvm.machine.state.TSMethodResult
import org.usvm.machine.state.TSState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

private val logger = KotlinLogging.logger {}

typealias TSStepScope = StepScope<TSState, EtsType, EtsStmt, TSContext>

@Suppress("UNUSED_PARAMETER")
class TSInterpreter(
    private val ctx: TSContext,
    private val applicationGraph: TSApplicationGraph,
) : UInterpreter<TSState>() {
    private val forkBlackList: UForkBlackList<TSState, EtsStmt> = UForkBlackList.createDefault()

    override fun step(state: TSState): StepResult<TSState> {
        val stmt = state.lastStmt
        val scope = StepScope(state, forkBlackList)

        val result = state.methodResult
        if (result is TSMethodResult.TSException) {
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

    private fun visitIfStmt(scope: TSStepScope, stmt: EtsIfStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val boolExpr = exprResolver
            // Don't want to lose UJoinedBoolExpr here for further fork.
            .resolveTSExpr(stmt.condition)
            ?.asExpr(ctx.boolSort)
            ?: run {
                logger.warn { "Failed to resolve condition: $stmt" }
                return
            }

        val succs = applicationGraph.successors(stmt).take(2).toList()
        val negStmt = succs[0]
        val posStmt = succs[1]

        scope.forkWithBlackList(
            boolExpr,
            posStmt,
            negStmt,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) }
        )
    }

    private fun visitReturnStmt(scope: TSStepScope, stmt: EtsReturnStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolveTSExpr(it) ?: return }
            ?: ctx.mkUndefinedValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitAssignStmt(scope: TSStepScope, stmt: EtsAssignStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val expr = exprResolver.resolveTSExpr(stmt.rhv) ?: return
        val lvalue = exprResolver.resolveLValue(stmt.lhv)

        scope.doWithState {
            memory.write(lvalue, expr.asExpr(lvalue.sort), guard = ctx.trueExpr)

            val nextStmt = stmt.nextStmt ?: return@doWithState
            newStmt(nextStmt)
        }
    }

    private fun visitCallStmt(scope: TSStepScope, stmt: EtsCallStmt) {
        TODO()
    }

    private fun visitThrowStmt(scope: TSStepScope, stmt: EtsThrowStmt) {
        TODO()
    }

    private fun visitGotoStmt(scope: TSStepScope, stmt: EtsGotoStmt) {
        TODO()
    }

    private fun visitNopStmt(scope: TSStepScope, stmt: EtsNopStmt) {
        TODO()
    }

    private fun visitSwitchStmt(scope: TSStepScope, stmt: EtsSwitchStmt) {
        TODO()
    }

    private fun exprResolverWithScope(scope: TSStepScope): TSExprResolver =
        TSExprResolver(
            ctx,
            scope,
            ::mapLocalToIdx
        ) { m, idx ->
            localVarToSort[m]?.get(idx)
        }

    // (method, localName) -> idx
    private val localVarToIdx: MutableMap<EtsMethod, MutableMap<String, Int>> = hashMapOf()

    // (method, localIdx) -> sort
    private val localVarToSort: MutableMap<EtsMethod, MutableMap<Int, USort>> = hashMapOf()

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

    fun getInitialState(method: EtsMethod, targets: List<TSTarget>): TSState {
        val state = TSState(
            ctx = ctx,
            ownership = MutabilityOwnership(),
            entrypoint = method,
            targets = UTargetsSet.from(targets),
            exprTransformer = TSExprTransformer(ctx)
        )

        val solver = ctx.solver<EtsType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parametersWithThisCount, method.localsCount)
        state.newStmt(method.cfg.instructions.first())

        return state
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt: EtsStmt?
        get() = applicationGraph.successors(this).firstOrNull()
}
