package org.usvm.samples

import TestOptions
import org.junit.jupiter.api.Test
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.taint.*
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

    private fun verifyDataFlowResult(
        programName: String,
        entryPointMethodName: String,
        sourceMethodConfig: SourceMethodConfig,
        cleanerMethodConfig: CleanerMethodConfig? = null,
        sinkMethodConfig: SinkMethodConfig? = null,
        builtInOptions: List<TaintBuiltInOption>? = null,
        mode: VerificationMode = VerificationMode.CONFIRMATION,
    ) {
        TestOptions.VERIFY_TRACE = true

        val project = getProject(programName)
        val fileTaintAnalyzer = TaintAnalyzer(project)

        val sinkResults = fileTaintAnalyzer.analyseOneCase(
            caseTaintConfig = CaseTaintConfig(
                sourceMethodConfigs = listOf(sourceMethodConfig),
                sinkMethodConfigs = listOfNotNull(sinkMethodConfig),
                cleanerMethodConfigs = listOfNotNull(cleanerMethodConfig),
                builtInOptions = builtInOptions ?: emptyList(),
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
        private val sourceMethodConfig = SourceMethodConfig(
            methodName = "getUserData"
        )
        private val sinkMethodConfig = SinkMethodConfig(
            methodName = "log",
            position = Argument(1)
        )

        @Test
        fun `test false positive refutation of password exposure`() {
            verifyDataFlowResult(
                programName = "passwordExposureFP",
                entryPointMethodName = "usage1",
                sourceMethodConfig = sourceMethodConfig,
                sinkMethodConfig = sinkMethodConfig,
                mode = VerificationMode.REFUTATION
            )
        }

        @Test
        fun `test confirmation of password exposure`() {
            verifyDataFlowResult(
                programName = "passwordExposureFP",
                entryPointMethodName = "usage2",
                sourceMethodConfig = sourceMethodConfig,
                sinkMethodConfig = sinkMethodConfig,
            )
        }

        @Test
        fun `simplified test false positive refutation of password exposure`() {
            verifyDataFlowResult(
                programName = "passwordExposureFP2",
                entryPointMethodName = "usage2",
                sourceMethodConfig = sourceMethodConfig,
                sinkMethodConfig = sinkMethodConfig,
                mode = VerificationMode.REFUTATION
            )
        }

        @Test
        fun `simplified test confirmation of password exposure`() {
            verifyDataFlowResult(
                programName = "passwordExposureFP2",
                entryPointMethodName = "usage1",
                sourceMethodConfig = sourceMethodConfig,
                sinkMethodConfig = sinkMethodConfig,
            )
        }
    }

    @Nested
    inner class UntrustedLoopBoundTest {
        private val programName = "untrustedLoopBound"
        private val sourceMethodConfig = SourceMethodConfig(
            methodName = "getUserData",
            markName = "UNTRUSTED"
        )
        private val builtInOptions = listOf(UntrustedLoopBoundSinkCheck)

        @Test
        fun `test untrusted loop bound confirmation in for loop`() {
            verifyDataFlowResult(
                programName = programName,
                entryPointMethodName = "forLoop",
                sourceMethodConfig = sourceMethodConfig,
                builtInOptions = builtInOptions,
            )
        }

        @Test
        fun `test untrusted loop bound confirmation in while loop`() {
            verifyDataFlowResult(
                programName = programName,
                entryPointMethodName = "whileLoop",
                sourceMethodConfig = sourceMethodConfig,
                builtInOptions = builtInOptions,
            )
        }

        @Test
        fun `test untrusted loop bound refutation in for loop with preliminary check`() {
            verifyDataFlowResult(
                programName = programName,
                entryPointMethodName = "verifiedForLoop",
                sourceMethodConfig = sourceMethodConfig,
                builtInOptions = builtInOptions,
                mode = VerificationMode.REFUTATION
            )
        }

        @Test
        fun `test untrusted loop bound refutation in while loop with preliminary check`() {
            verifyDataFlowResult(
                programName = programName,
                entryPointMethodName = "verifiedWhileLoop",
                sourceMethodConfig = sourceMethodConfig,
                builtInOptions = builtInOptions,
                mode = VerificationMode.REFUTATION
            )
        }
    }

    @Nested
    inner class UntrustedArraySizeTest {
        private val programName = "untrustedArraySize"
        private val sourceMethodConfig = SourceMethodConfig(
            methodName = "getNumber",
            markName = "UNTRUSTED"
        )
        private val builtInOptions = listOf(UntrustedArraySizeSinkCheck)

        @Test
        fun `test untrusted array size confirmation`() {
            verifyDataFlowResult(
                programName = programName,
                entryPointMethodName = "getArray",
                sourceMethodConfig = sourceMethodConfig,
                builtInOptions = builtInOptions,
            )
        }

        @Test
        fun `test untrusted array size refutation in method when size is checked`() {
            verifyDataFlowResult(
                programName = programName,
                entryPointMethodName = "safeGetArray",
                sourceMethodConfig = sourceMethodConfig,
                builtInOptions = builtInOptions,
                mode = VerificationMode.REFUTATION
            )
        }
    }
}
