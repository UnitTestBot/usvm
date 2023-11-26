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
import kotlin.time.Duration

class JacoDbTest {
    @TestFactory
    fun jacodbTestLong(): Collection<DynamicTest> {
        val pkg = Converter.unpack(Parser().deserialize("out/usvm_examples.json"))
        val program = GoProgram(listOf(pkg))
        val machine = GoMachine(program, options, customOptions)
        return methods(pkg).filter { it.metName in longMethods }.map {
            DynamicTest.dynamicTest(it.metName) {
                println(measureTimeMillis { println(machine.analyzeAndResolve(pkg, it.metName)) })
            }
        }
    }

    @TestFactory
    fun jacodbTestFast(): Collection<DynamicTest> {
        val pkg = Converter.unpack(Parser().deserialize("out/usvm_examples.json"))
        val program = GoProgram(listOf(pkg))
        val machine = GoMachine(program, options, customOptions)
        return methods(pkg)
            .filter { it.metName !in longMethods }
            .let { if (whitelist.isEmpty()) it else it.filter { m -> m.metName in whitelist } }
            .map {
                DynamicTest.dynamicTest(it.metName) {
                    println(measureTimeMillis { println(machine.analyzeAndResolve(pkg, it.metName)) })
                }
            }
    }

    @TestFactory
    fun jacodbTestImports(): Collection<DynamicTest> {
        val packages = Files.list(Paths.get("out"))
            .filter { p -> p.extension != "yaml" }
            .map { Converter.unpack(Parser().deserialize(it.pathString)) }
            .collect(Collectors.toList())
        val program = GoProgram(packages)
        val machine = GoMachine(program, options, customOptions)
        val pkg = program.findPackage("usvm/examples/imports")
        return methods(pkg).filter { it.metName !in longMethods }.map {
            DynamicTest.dynamicTest(it.metName) {
                println(measureTimeMillis { println(machine.analyzeAndResolve(pkg, it.metName)) })
            }
        }
    }

    @TestFactory
    fun jacodbTestStdStrings(): Collection<DynamicTest> {
        val packages = Files.list(Paths.get("out"))
            .filter { p -> p.extension != "yaml" }
            .map { Converter.unpack(Parser().deserialize(it.pathString)) }
            .collect(Collectors.toList())
        val program = GoProgram(packages)
        val machine = GoMachine(program, options, customOptions)
        val pkg = program.findPackage("flag")
        return methods(pkg).filter { it.metName !in longMethods }.map {
            DynamicTest.dynamicTest(it.metName) {
                println(measureTimeMillis { println(machine.analyzeAndResolve(pkg, it.metName)) })
            }
        }
    }

    private fun methods(pkg: GoPackage): List<GoMethod> = pkg.methods.filter { !it.metName.contains('$') }

    private val options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
//        timeout = 5_000.milliseconds,
        timeout = Duration.INFINITE, // for debug
        stepsFromLastCovered = 1000000L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )

    private val customOptions: GoMachineOptions = GoMachineOptions(
        failOnNotFullCoverage = false,
        uncoveredMethods = listOf("panicRecoverComplex")
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