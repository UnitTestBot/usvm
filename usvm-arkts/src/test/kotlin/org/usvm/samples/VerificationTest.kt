package org.usvm.samples

import TestOptions
import org.junit.jupiter.api.Test
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.taint.CaseTaintConfig
import org.jacodb.panda.taint.SinkMethodConfig
import org.jacodb.panda.taint.SourceMethodConfig
import org.jacodb.panda.taint.TaintAnalyzer
import org.jacodb.taint.configuration.Argument
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested

enum class VerificationMode {
    CONFIRMATION,
    REFUTATION
}


class VerificationTest : PandaMethodTestRunner() {
    private fun verifyTrace(dataFlowTrace: List<PandaInst>, symbolicTrace: List<PandaInst>, mode: VerificationMode = VerificationMode.CONFIRMATION): Boolean {
        val reversedSymbolicTrace = symbolicTrace.reversed()
        var index = 0
        for (inst in reversedSymbolicTrace) {
            if (index == dataFlowTrace.size) {
                return (mode == VerificationMode.CONFIRMATION)
            }
            if (dataFlowTrace[index].location.toString() == inst.location.toString()) {
                index++
            }
        }
        return (mode == VerificationMode.REFUTATION)
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

    @Nested
    inner class PasswordExposureTest {

        private fun verifyDataFlowResult(programName: String, entryPointMethodName: String, mode: VerificationMode = VerificationMode.CONFIRMATION) {
            TestOptions.VERIFY_TRACE = true

            val project = getProject(programName)
            val fileTaintAnalyzer = TaintAnalyzer(project)

            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                caseTaintConfig = CaseTaintConfig(
                    sourceMethodConfigs = listOf(SourceMethodConfig("getUserData")),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                            position = Argument(1)
                        )
                    ),
                    startMethodNamesForAnalysis = listOf(entryPointMethodName)
                ),
                withTrace = true
            )

            val traceToVerify: List<PandaInst> = sinkResults.first().trace!!
            discoverPropertiesWithTraceVerification<Any>(
                methodIdentifier = MethodDescriptor(
                    className = programName,
                    methodName = entryPointMethodName,
                    argumentsNumber = 0
                ),
                { _, trace -> verifyTrace(traceToVerify, trace, mode) }
            )
        }
        @Test
        fun `test false positive refutation of password exposure`() {
            verifyDataFlowResult("passwordExposureFP","usage1", VerificationMode.REFUTATION)
        }

        @Test
        fun `test confirmation of password exposure`() {
            verifyDataFlowResult("passwordExposureFP","usage2")
        }

        @Test
        fun `simplified test false positive refutation of password exposure`() {
            verifyDataFlowResult("passwordExposureFP2","usage2", VerificationMode.REFUTATION)
        }

        @Test
        fun `simplified test confirmation of password exposure`() {
            verifyDataFlowResult("passwordExposureFP2","usage1")
        }
    }
}
