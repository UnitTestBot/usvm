package org.usvm.samples

import org.usvm.interpreter.PythonExecutionState
import org.usvm.interpreter.PythonMachine
import org.usvm.language.PythonCallable
import org.usvm.language.PythonProgram
import org.usvm.test.util.TestRunner
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

fun main() {
    val functionDescriptor = FunctionDescriptor(
        fileName = "./src/samples/python/SimpleExample.py",
        functionName = "f",
        numberOfArguments = 3
    )
    PythonTestRunner().internalCheck(functionDescriptor)
}

class PythonTestRunner : TestRunner<PythonTest, FunctionDescriptor, Any, PythonCoverage>() {

    @OptIn(ExperimentalTime::class)
    fun internalCheck(
        functionDescriptor: FunctionDescriptor,
    ) {
        val file = File(functionDescriptor.fileName)
        val program = PythonProgram(file.readText())
        val function = PythonCallable.constructCallableFromName(
            functionDescriptor.numberOfArguments,
            functionDescriptor.functionName
        )

        val machine = PythonMachine(program)

        val (results, time) = measureTimedValue {
            machine.use {
                it.analyzeWithProducingStates(function)
            }
        }

        val tests = results.extractInputValuesWithResult()

        println("Finished in $time milliseconds.")
        println(tests)
    }

    private fun List<PythonExecutionState>.extractInputValuesWithResult(): List<PythonTest> =
        this.map {
            TODO()
        }

    override val typeTransformer: (Any?) -> Any
        get() = { _ -> TODO() }
    override val checkType: (Any, Any) -> Boolean
        get() = { _, _ -> true }
    override val runner: (FunctionDescriptor) -> List<PythonTest>
        get() = TODO("Not yet implemented")
    override val coverageRunner: (List<PythonTest>) -> PythonCoverage
        get() = TODO("Not yet implemented")
}

data class FunctionDescriptor(
    val fileName: String,
    val functionName: String,
    val numberOfArguments: Int
)

data class PythonTest(val inputValues: List<Any?>, val result: Any?)
data class PythonCoverage(val int: Int)