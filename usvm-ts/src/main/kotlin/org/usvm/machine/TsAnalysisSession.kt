package org.usvm.machine

/**
 * One analysis whose queue and statistics survive a phase yield. The owning [TsMachine] must stay open.
 * Closing the session discards pending work without closing the machine or its solvers.
 */
class TsAnalysisSession internal constructor(
    advancePhase: (ULong?) -> TsMachineAnalysisResult,
    analysisFinished: () -> Boolean,
    release: () -> Unit,
) : AutoCloseable {
    private var advancePhase: ((ULong?) -> TsMachineAnalysisResult)? = advancePhase
    private var analysisFinished: (() -> Boolean)? = analysisFinished
    private var release: (() -> Unit)? = release
    private var advancing = false

    /** True after path exhaustion or a configured analysis limit, rather than a phase yield. */
    val isFinished: Boolean
        get() = analysisFinished?.invoke() ?: true

    /**
     * Runs at most [maxSteps] additional interpreter steps. Analysis-wide limits remain cumulative.
     * A finished analysis returns its last snapshot without executing or notifying observers again.
     * Bounded phases currently require one runnable entry point; multi-entry scheduling is uninterrupted.
     */
    fun advance(maxSteps: ULong? = null): TsMachineAnalysisResult {
        val phase = checkNotNull(advancePhase) { "Analysis session is closed" }
        check(!advancing) { "Analysis session is already advancing" }

        advancing = true
        var succeeded = false
        try {
            val result = phase(maxSteps)
            succeeded = true
            return result
        } finally {
            advancing = false
            if (!succeeded) close()
        }
    }

    override fun close() {
        check(!advancing) { "Cannot close an advancing analysis session" }
        val releaseAnalysis = release ?: return

        advancePhase = null
        analysisFinished = null
        release = null
        releaseAnalysis()
    }
}
