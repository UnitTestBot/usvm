package org.usvm.jacodb

import org.jacodb.go.api.GoProject
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.jacodb.gen.StartDeserializer
import org.usvm.jacodb.gen.ssa_Program
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis

class JacoDbTest {
    @Test
    fun testMax2() {
        test("Max2")
    }

    @Test
    fun testMax2Improved() {
        test("Max2Improved")
    }

    @Test
    fun testLoopSimple() {
        test("loopSimple")
    }

    @Test
    fun testPanicRecover() {
        test("panicRecover")
    }

    private fun test(name: String) {
        var project: GoProject

        val stream = File("./src/main/kotlin/org/usvm/jacodb/gen/filled.gzip").inputStream()
        val reader = GZIPInputStream(stream).bufferedReader()

        val stopwatch = measureTimeMillis {
            val res = StartDeserializer(reader) as ssa_Program

            project = res.createJacoDBProject()
        }

        println(stopwatch)

        val machine = GoMachine(project, UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH)))
        println(machine.analyzeAndResolve(project.methods.find { it.metName == name }!!))
    }
}