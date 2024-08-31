package org.usvm

import org.jacodb.go.api.GoProject
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis
import kotlin.time.Duration

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

    @Test
    fun testPanicRecoverSimple() {
        test("panicRecoverSimple")
    }

    private fun test(name: String) {
        val project = GoProject(emptyList())

        val stream = File("./src/main/kotlin/org/usvm/jacodb/gen/filled.gzip").inputStream()
        val reader = GZIPInputStream(stream).bufferedReader()

        val stopwatch = measureTimeMillis {
//            val res = StartDeserializer(reader) as ssa_Program

//            project = res.createJacoDBProject()
        }

        println(stopwatch)

        val machine = GoMachine(project, UMachineOptions(listOf(PathSelectionStrategy.FORK_DEPTH), timeout = Duration.INFINITE))
        println(machine.analyzeAndResolve(project.methods.find { it.metName == name }!!))
    }
}