package org.usvm.machine

import io.ksmt.sort.KSort
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaCallInst
import org.jacodb.panda.dynamic.api.PandaIfInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaLocal
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaThrowInst
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UExpr
import org.usvm.UInterpreter
import org.usvm.USort
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.state.PandaMethodResult
import org.usvm.machine.state.PandaState
import org.usvm.machine.state.lastStmt
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

typealias PandaStepScope = StepScope<PandaState, PandaType, PandaInst, PandaContext>

@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
class PandaInterpreter(private val ctx: PandaContext) : UInterpreter<PandaState>() {

    private val forkBlackList: UForkBlackList<PandaState, PandaInst> = UForkBlackList.createDefault()

    override fun step(state: PandaState): StepResult<PandaState> {
        val stmt = state.lastStmt
        val scope = StepScope(state, forkBlackList)

        val result = state.methodResult
        if (result is PandaMethodResult.PandaException) {
            TODO()
        }

        when (stmt) {
            is PandaIfInst -> visitIfStmt(scope, stmt)
            is PandaReturnInst -> visitReturnStmt(scope, stmt)
            is PandaAssignInst -> visitAssignInst(scope, stmt)
            is PandaCallInst -> visitCallStmt(scope, stmt)
            is PandaThrowInst -> visitThrowStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }

        return scope.stepResult()
    }

    fun getInitialState(method: PandaMethod, targets: List<PandaTarget>): PandaState {
        val state = PandaState(ctx, method, targets = UTargetsSet.from(targets))

        val solver = ctx.solver<PandaType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parameters.size, method.localVarsCount)
        state.pathNode += method.instructions.first()

        return state
    }

    private fun visitIfStmt(scope: PandaStepScope, stmt: PandaIfInst) {
        TODO()
    }

    private fun visitReturnStmt(scope: PandaStepScope, stmt: PandaReturnInst) {
        val exprResolver = PandaExprResolver(ctx, scope, ::mapLocalToIdxMapper, ::saveSortInfo, ::extractSortInfo)

        val method = requireNotNull(scope.calcOnState { callStack.lastMethod() })
        // TODO process the type
        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolvePandaExpr(it) }
            ?: error("TODO")

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    // TODO extract
    private fun PandaState.returnValue(valueToReturn: UExpr<out USort>) {
        val returnFromMethod = callStack.lastMethod()
        // TODO: think about it later
        val returnSite = callStack.pop()
        if (callStack.isNotEmpty()) {
            memory.stack.pop()
        }

        methodResult = PandaMethodResult.Success(returnFromMethod, valueToReturn)

        if (returnSite != null) {
            newStmt(returnSite)
        }
    }

    private fun visitAssignInst(scope: PandaStepScope, stmt: PandaAssignInst) {
        val exprResolver = PandaExprResolver(ctx, scope, ::mapLocalToIdxMapper, ::saveSortInfo, ::extractSortInfo)

        val expr = exprResolver.resolvePandaExpr(stmt.rhv) ?: return

        (stmt.lhv as? PandaLocalVar)?.let {
            if (expr.sort != ctx.anySort) {
                saveSortInfo(it, scope.calcOnState { lastEnteredMethod }, expr.sort)
            }
        }

        val lValue = exprResolver.resolveLValue(stmt.lhv, alternativeSortInfo = expr.sort) ?: return

        scope.doWithState {
            val nextStmt = stmt.nextStmt
            memory.write(lValue, expr)
            newStmt(nextStmt)
        }
    }


    private fun visitCallStmt(scope: PandaStepScope, stmt: PandaCallInst) {
        TODO()
    }

    private fun visitThrowStmt(scope: PandaStepScope, stmt: PandaThrowInst) {
        TODO()
    }

    // (method, localIdx) -> idx
    private val localVarToIdx = mutableMapOf<PandaMethod, MutableMap<Int, Int>>()

    // (method, localIdx) -> Sort
    private val additionalLocalVarSortInfo = mutableMapOf<PandaMethod, MutableMap<Int, KSort>>()

    // TODO: now we need to explicitly evaluate indices of registers, because we don't have specific ULValues
    private fun mapLocalToIdxMapper(method: PandaMethod, local: PandaLocal) =
        when (local) {
            is PandaLocalVar -> localVarToIdx
                .getOrPut(method) { mutableMapOf() }
                .run {
                    // TODO fix parameters count as in Java
                    getOrPut(local.index) { method.parameters.count() + size }
                }

            is PandaThis -> 0
            is PandaArgument -> local.index // TODO static????
            else -> error("Unexpected local: $local")
        }

    private fun saveSortInfo(pandaLocalVar: PandaLocalVar, method: PandaMethod, sort: USort) {
        additionalLocalVarSortInfo
            .getOrPut(method) { mutableMapOf() }
            .run {
                require(pandaLocalVar.index !in this.keys) { "TODO" }
                put(pandaLocalVar.index, sort)
            }
    }

    private fun extractSortInfo(pandaLocalVar: PandaLocalVar, method: PandaMethod) =
        additionalLocalVarSortInfo
            .getOrPut(method) { mutableMapOf() }
            .run {
                getOrPut(pandaLocalVar.index) { ctx.typeToSort(pandaLocalVar.type) }
            }


    private val PandaInst.nextStmt get() = location.let { it.method.instructions[it.index + 1] }
}
