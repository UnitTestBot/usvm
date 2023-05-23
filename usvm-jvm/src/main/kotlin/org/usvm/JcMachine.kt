package org.usvm

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcTypedMethod
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.combinators.InterleavedSelector
import org.usvm.state.JcMethodResult
import org.usvm.state.JcState

class JcMachine(
    cp: JcClasspath,
    val maxStates: Int = 40,
) : UMachine<JcState, JcTypedMethod>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem)
    private val ctx = JcContext(cp, components)

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
        interpreter.getInitialState(method)

    private fun isInterestingState(state: JcState): Boolean {
        return state.callStack.isNotEmpty() && state.methodResult !is JcMethodResult.Exception
    }
}