package org.usvm

import io.ksmt.utils.asExpr
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
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.memory.URegisterStackLValue
import org.usvm.solver.USatResult
import org.usvm.state.TSMethodResult
import org.usvm.state.TSState
import org.usvm.state.lastStmt
import org.usvm.state.localIdx
import org.usvm.state.newStmt
import org.usvm.state.parametersWithThisCount
import org.usvm.state.returnValue
import org.usvm.targets.UTargetsSet

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
        TODO()
    }

    private fun visitReturnStmt(scope: TSStepScope, stmt: EtsReturnStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val method = requireNotNull(scope.calcOnState { callStack.lastMethod() })
        val returnType = method.returnType

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolveTSExpr(it, returnType) ?: return }
            ?: ctx.mkUndefinedValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitAssignStmt(scope: TSStepScope, stmt: EtsAssignStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val lvalue = exprResolver.resolveLValue(stmt.lhv) ?: return
        val expr = exprResolver.resolveTSExpr(stmt.rhv, stmt.lhv.type) ?: return

        scope.doWithState {
            memory.write(lvalue, expr)
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

    private fun exprResolverWithScope(scope: TSStepScope) =
        TSExprResolver(
            ctx,
            scope,
            ::mapLocalToIdxMapper,
        )

    // (method, localName) -> idx
    private val localVarToIdx = mutableMapOf<EtsMethod, MutableMap<String, Int>>()

    private fun mapLocalToIdxMapper(method: EtsMethod, local: EtsValue) =
        when (local) {
            is EtsLocal -> localVarToIdx
                .getOrPut(method) { mutableMapOf() }
                .run {
                    getOrPut(local.name) { method.parametersWithThisCount + size }
                }

            is EtsThis -> 0
            is EtsParameterRef -> method.localIdx(local.index)
            else -> error("Unexpected local: $local")
        }


    fun getInitialState(method: EtsMethod, targets: List<TSTarget>): TSState {
        val state = TSState(ctx, method, targets = UTargetsSet.from(targets))

        with(ctx) {
            val params = List(method.parameters.size) { idx ->
                URegisterStackLValue(addressSort, idx)
            }
            val refs = params.map { state.memory.read(it) }

            // TODO check correctness of constraints and process this instance
            state.pathConstraints += mkAnd(refs.map { mkEq(it.asExpr(addressSort), nullRef).not() })
        }

        val solver = ctx.solver<EtsType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parameters.size, method.localsCount)
        state.pathNode += method.cfg.instructions.first()

        return state
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt get() = applicationGraph.successors(this).firstOrNull()
}
