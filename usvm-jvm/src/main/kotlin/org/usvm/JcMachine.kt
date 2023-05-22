package org.usvm

import kotlinx.collections.immutable.persistentListOf
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.combinators.InterleavedSelector
import org.usvm.state.JcMethodResult
import org.usvm.state.JcState
import org.usvm.state.addEntryMethodCall

class JcMachine(
    cp: JcClasspath,
    val maxStates: Int = 40,
) : UMachine<JcState, JcTypedMethod>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem)
    private val ctx = JcContext(cp, components)
    private val solver = ctx.solver<JcTypedField, JcType, JcTypedMethod>()

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    fun analyze(method: JcTypedMethod): List<JcState> {
        val collectedStates = mutableListOf<JcState>()

        run(
            method,
            onState = { state ->
                if (!isInterestingState(state)) {
                    collectedStates += state
                }
            },
            continueAnalyzing = ::isInterestingState,
            stoppingStrategy = { collectedStates.size >= maxStates }
        )
        return collectedStates
    }

    override fun getInterpreter(target: JcTypedMethod): UInterpreter<JcState> =
        interpreter

    override fun getPathSelector(target: JcTypedMethod): UPathSelector<JcState> {
        val ps = InterleavedSelector<JcState>(DfsPathSelector(), BfsPathSelector())
        val state = getInitialState(target)
        ps.add(listOf(state))
        return ps
    }

    private fun getInitialState(method: JcTypedMethod): JcState =
        JcState(ctx).apply {
            addEntryMethodCall(applicationGraph, method)
            val model = solver.emptyModel()
            models = persistentListOf(model)
        }

    private fun isInterestingState(state: JcState): Boolean {
        return state.callStack.isNotEmpty() && state.methodResult !is JcMethodResult.Exception
    }
}