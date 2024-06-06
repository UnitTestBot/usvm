package org.usvm.samples

import TestOptions
import org.junit.jupiter.api.Test
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.taint.CaseTaintConfig
import org.jacodb.panda.taint.SinkMethodConfig
import org.jacodb.panda.taint.SourceMethodConfig
import org.jacodb.panda.taint.TaintAnalyzer


class VerificationTest : PandaMethodTestRunner() {
    private fun verifyTrace(dataFlowTrace: List<PandaInst>, symbolicTrace: List<PandaInst>): Boolean {
        val reversedSymbolicTrace = symbolicTrace.reversed()
        var index = 0
        for (inst in reversedSymbolicTrace) {
            if (index == dataFlowTrace.size) {
                return true
            }
            if (dataFlowTrace[index].location.toString() == inst.location.toString()) {
                index++
            }
        }
        // temporary adhoc because of console.log
        if (index == dataFlowTrace.size - 1) {
            return true
        }
        return false
    }

    @Test
    fun `test trace verification for method add in basicSamples file`() {
        TestOptions.VERIFY_TRACE = true
        val traceToVerify = listOf<PandaInst>()
        discoverPropertiesWithTraceVerification<Double>(
            methodIdentifier = MethodDescriptor(
                className = "BasicSamples",
                methodName = "add",
                argumentsNumber = 0
            ),
            { result, trace -> verifyTrace(traceToVerify, trace) }
        )
    }

    @Test
    fun `test false positive refutation of password exposure`() {
        TestOptions.VERIFY_TRACE = true

        val project = getProject("passwordExposureFP")
        val fileTaintAnalyzer = TaintAnalyzer(project)

        val sinkResults = fileTaintAnalyzer.analyseOneCase(
            caseTaintConfig = CaseTaintConfig(
                sourceMethodConfigs = listOf(SourceMethodConfig("getUserData")),
                sinkMethodConfigs = listOf(
                    SinkMethodConfig(
                        methodName = "log",
                    )
                ),
                startMethodNamesForAnalysis = listOf("usage1")
            ),
            withTrace = true
        )

        val traceToVerify: List<PandaInst> = sinkResults.first().trace!!
        discoverPropertiesWithTraceVerification<Any>(
            methodIdentifier = MethodDescriptor(
                className = "passwordExposureFP",
                methodName = "usage1",
                argumentsNumber = 0
            ),
            { _, trace -> verifyTrace(traceToVerify, trace) }
        )
    }
}
