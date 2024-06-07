import org.usvm.UMachineOptions
import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.interpreters.concrete.IllegalOperationException
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.runner.CustomPythonTestRunner
import org.usvm.runner.manual.analyzers.OrdinaryAnalyzer
import org.usvm.runner.manual.program.LocalProgramProvider
import kotlin.time.Duration.Companion.seconds

fun main() {
    /**
     * See:
     *  - [org.usvm.runner.manual.program.sampleStringFunction]
     *  - [org.usvm.runner.manual.program.sampleFunction]
     *  - [org.usvm.runner.manual.program.LocalProgramProvider]
     * */
    val program = LocalProgramProvider(
        "/home/tochilinak/Documents/projects/utbot/Python/dynamic_programming",
    )

    /**
     * TODO
     * */
    val analyzer = OrdinaryAnalyzer

    analyzer.run(program)
}


private fun checkConcolicAndConcrete(runConfig: RunConfig) {
    val (program, typeSystem, functions) = runConfig
    val runner = CustomPythonTestRunner(
        program,
        typeSystem,
        UMachineOptions(stepLimit = 60U, timeout = 60.seconds),
        allowPathDiversions = true
    )
    runner.timeoutPerRunMs = 10_000
    functions.forEach { function ->
        println("Running ${function.tag}...")
        try {
            val comparator = runner.standardConcolicAndConcreteChecks
            when (val argsNum = function.numberOfArguments) {
                0 -> runner.check0NoPredicates(function, comparator)
                1 -> runner.check1NoPredicates(function, comparator)
                2 -> runner.check2NoPredicates(function, comparator)
                3 -> runner.check3NoPredicates(function, comparator)
                4 -> runner.check4NoPredicates(function, comparator)
                else -> println("${function.tag} ignored because it has $argsNum arguments")
            }
        } catch (e: IllegalOperationException) {
            println("Illegal operation while analyzing: ${e.operation}\n")
        }
    }
}

private data class RunConfig(
    val program: PyProgram,
    val typeSystem: PythonTypeSystem,
    val functions: List<PyUnpinnedCallable>,
)
