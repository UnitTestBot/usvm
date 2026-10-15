package org.usvm.ts.calls

import org.usvm.ts.pbt.model.JsConcreteValue

/** Reuses only source preparation; every replay still starts a fresh supervised Node.js process. */
internal class CallsClassScenarioReplayers(
    private val harness: CallsClassPreparedHarness,
    private val instrumentation: CallsClassSourceInstrumentation,
) : AutoCloseable {
    private val originalReplayers = mutableMapOf<Int, OriginalTypeScriptClassReplayer>()
    private val instrumentedReplayers = mutableMapOf<Int, OriginalTypeScriptClassReplayer>()

    fun replay(
        scenarioIndex: Int,
        inputs: List<JsConcreteValue>,
        timeoutMillis: Long,
        instrumented: Boolean,
    ): CallsClassReplayObservation {
        val replayers = if (instrumented) instrumentedReplayers else originalReplayers
        val replayer = replayers.getOrPut(scenarioIndex) {
            OriginalTypeScriptClassReplayer(
                sourceRoot = harness.sourceRoot,
                function = harness.functions[scenarioIndex],
                observationFields = harness.observations[scenarioIndex],
                sourceOverride = if (instrumented) instrumentation.instrumentedSource else null,
                overrideSourceFile = if (instrumented) harness.surface.sourceFile else null,
                counterGlobalName = if (instrumented) instrumentation.hitGlobalName else null,
            )
        }

        return replayer.replay(inputs = inputs, timeoutMillis = timeoutMillis)
    }

    override fun close() {
        var failure: Throwable? = null
        for (replayer in originalReplayers.values + instrumentedReplayers.values) {
            val error = runCatching { replayer.close() }.exceptionOrNull()
            if (error == null) continue

            val firstFailure = failure
            if (firstFailure == null) {
                failure = error
            } else {
                firstFailure.addSuppressed(error)
            }
        }
        originalReplayers.clear()
        instrumentedReplayers.clear()
        failure?.let { throw it }
    }
}
