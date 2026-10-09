package org.usvm.interpreter

import mu.KLogging
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoType
import org.jacodb.go.api.PointerType
import org.usvm.GoCall
import org.usvm.GoContext
import org.usvm.GoExprVisitor
import org.usvm.GoInstVisitor
import org.usvm.GoProgram
import org.usvm.GoTarget
import org.usvm.NULL_ADDRESS
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.solver.USatResult
import org.usvm.state.GoFlowStatus
import org.usvm.state.GoState
import org.usvm.state.advanceArrayValueCopy
import org.usvm.statistics.ApplicationGraph
import org.usvm.targets.UTargetsSet

typealias GoStepScope = StepScope<GoState, GoType, GoInst, GoContext>

class GoInterpreter(
    private val ctx: GoContext,
    private val program: GoProgram,
    private val applicationGraph: ApplicationGraph<GoMethod, GoInst>,
    private var forkBlackList: UForkBlackList<GoState, GoInst> = UForkBlackList.createDefault(),
) : UInterpreter<GoState>() {
    internal val unsupportedOperations: MutableList<UnsupportedOperationException> = mutableListOf()

    @Suppress("NestedBlockDepth") // Initializes pointer chains for package globals.
    fun getInitialState(method: GoMethod, targets: List<GoTarget> = emptyList()): GoState = with(ctx) {
        val initOwnership = MutabilityOwnership()
        val state = GoState(ctx, initOwnership, method, targets = UTargetsSet.from(targets))

        for (global in program.globals) {
            var type = global.type
            var ref = mkConcreteHeapRef(NULL_ADDRESS)
            var depth = 0
            while (type is PointerType) {
                type = type.baseType
                depth++
            }
            repeat(depth) {
                ref = if (ref.address != NULL_ADDRESS) {
                    state.mkPointer(type, ref)
                } else {
                    state.mkPointer(type)
                }
                type = PointerType(type)
            }
            addGlobal(global, ref)
        }

        val entrypoint = method.blocks[0].instructions[0]
        state.addCall(GoCall(method, entrypoint))
        var previousEntrypoint = entrypoint
        for (m in program.findInitMethods(method.packageName) + program.findOsInitMethods()) {
            state.addCall(GoCall(m, applicationGraph.entryPoints(m).first()), previousEntrypoint)
            previousEntrypoint = m.blocks[0].instructions[0]
        }

        val model = (solver<GoType>().check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        return state
    }

    override fun step(state: GoState): StepResult<GoState> {
        val inst = state.currentStatement
        val scope = GoStepScope(state, forkBlackList)
        if (state.data.pendingArrayCopy != null) {
            advanceArrayValueCopy(scope)
            return scope.stepResult()
        }

        val exprVisitor = GoExprVisitor(ctx, program, scope, applicationGraph)
        val instVisitor = GoInstVisitor(ctx, program, scope, exprVisitor, applicationGraph)

        logger.debug("State {}: Step: {}", state.id, inst)

        try {
            val nextInst = next(state, inst, instVisitor)
            if (nextInst !is GoNullInst) {
                state.newInst(nextInst)
            }
        } catch (_: GoStepAbort) {
            // The scope already records the surviving panic or forked states.
        } catch (error: UnsupportedOperationException) {
            unsupportedOperations += error
            throw error
        }
        return scope.stepResult()
    }

    private fun next(state: GoState, inst: GoInst, instVisitor: GoInstVisitor): GoInst {
        val method = state.lastEnteredMethod
        return when (state.data.flowStatus) {
            GoFlowStatus.NORMAL -> {
                inst.accept(instVisitor)
            }
            GoFlowStatus.DEFER -> {
                val deferred = state.data.getDeferredCalls()
                if (deferred.isEmpty()) {
                    state.data.flowStack.removeLast()
                    return next(state, inst, instVisitor)
                }

                state.addCall(deferred.removeLast(), inst)
                return GoNullInst(method)
            }

            GoFlowStatus.PANIC -> {
                if (!state.isExceptional) { // recovered
                    state.data.flowStack.removeLast()
                    val function = method as GoFunction
                    function.setRecover()
                    return checkNotNull(
                        function.recover
                    ) { "Recovered function has no recovery block" }.instructions.first()
                }

                if (state.data.getDeferredCalls().isEmpty()) {
                    state.handlePanic()
                    return GoNullInst(method)
                }

                state.runDefers()
                return next(state, inst, instVisitor)
            }
        }
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
