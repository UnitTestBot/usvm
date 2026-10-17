package org.usvm

import org.jacodb.go.api.GoMethod
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.usvm.model.Converter
import org.usvm.model.Parser
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

class JacoDbTest {
    @TestFactory
    fun jacodbTestLong(): Collection<DynamicTest> {
        val pkg = Converter.unpack(Parser().deserialize("out/usvm_examples.json"))
        val program = GoProgram(listOf(pkg))

        return methods(pkg).filter { it.metName in longMethods }.map { method ->
            DynamicTest.dynamicTest(method.metName) {
                GoMachine(program, options, customOptions).use { machine ->
                    analyzeAndLog(machine, pkg, method.metName)
                }
            }
        }
    }

    @TestFactory
    fun jacodbTestFast(): Collection<DynamicTest> {
        val pkg = Converter.unpack(Parser().deserialize("out/usvm_examples.json"))
        val program = GoProgram(listOf(pkg))

        return methods(pkg)
            .filter { it.metName !in longMethods }
            .let { if (whitelist.isEmpty()) it else it.filter { m -> m.metName in whitelist } }
            .map { method ->
                DynamicTest.dynamicTest(method.metName) {
                    GoMachine(program, options, customOptions).use { machine ->
                        analyzeAndLog(machine, pkg, method.metName)
                    }
                }
            }
    }

    @TestFactory
    fun jacodbTestImports(): Collection<DynamicTest> {
        val packages = Files.list(Paths.get("out")).use { paths ->
            paths.filter { path -> path.extension != "yaml" }
                .map { Converter.unpack(Parser().deserialize(it.pathString)) }
                .collect(Collectors.toList())
        }
        val program = GoProgram(packages)
        val pkg = program.findPackage("usvm/examples/imports")

        return methods(pkg).filter { it.metName !in longMethods }.map { method ->
            DynamicTest.dynamicTest(method.metName) {
                GoMachine(program, options, customOptions).use { machine ->
                    analyzeAndLog(machine, pkg, method.metName)
                }
            }
        }
    }

    @TestFactory
    fun jacodbTestStdStrings(): Collection<DynamicTest> {
        val packages = Files.list(Paths.get("out")).use { paths ->
            paths.filter { path -> path.extension != "yaml" }
                .map { Converter.unpack(Parser().deserialize(it.pathString)) }
                .collect(Collectors.toList())
        }
        val program = GoProgram(packages)
        val pkg = program.findPackage("flag")

        return methods(pkg).filter { it.metName !in longMethods }.map { method ->
            DynamicTest.dynamicTest(method.metName) {
                GoMachine(program, options, customOptions).use { machine ->
                    analyzeAndLog(machine, pkg, method.metName)
                }
            }
        }
    }

    private fun analyzeAndLog(machine: GoMachine, pkg: GoPackage, methodName: String) {
        val elapsedMillis = measureTimeMillis {
            val result = machine.analyzeAndResolve(pkg, methodName)
            logger.info { result }
        }
        logger.info { "Analysis took $elapsedMillis ms" }
    }

    private fun methods(pkg: GoPackage): List<GoMethod> = pkg.methods.filter { !it.metName.contains('$') }

    private val options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        timeout = 30.seconds,
        stepsFromLastCovered = 1_000_000L,
        solverTimeout = 5.seconds,
        typeOperationsTimeout = 5.seconds,
    )

    private val customOptions: GoMachineOptions = GoMachineOptions(
        failOnNotFullCoverage = false,
        uncoveredMethods = listOf("panicRecoverComplex"),
    )

    private val longMethods = arrayOf("loopInfinite", "loopInner", "loopCollatz", "mapLoopLen", "canVisitAllRooms")

    private val whitelist = arrayOf<String>(
//        "sliceCustomAppend"
//        "appendErrorStrings"
//        "(usvm/examples.Person).WithName"
//        "sliceCustomAppend"
//        "shiftErrorToString"
//        "mapCustomDeleteSimple"
    )
}
