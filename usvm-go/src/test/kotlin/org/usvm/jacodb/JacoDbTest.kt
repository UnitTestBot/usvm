package org.usvm.jacodb

import org.jacodb.go.api.GoProject
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.jacodb.gen.StartDeserializer
import org.usvm.jacodb.gen.ssa_Program
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis

class JacoDbTest {
    @Test
    fun testMax2() {
        var project: GoProject

        val stream = File("./src/main/kotlin/org/usvm/jacodb/gen/filled.gzip").inputStream()
        val reader = GZIPInputStream(stream).bufferedReader()

        val stopwatch = measureTimeMillis {
            val res = StartDeserializer(reader) as ssa_Program

            project = res.createJacoDBProject()
        }

        println(stopwatch)

        val machine = GoMachine(project, UMachineOptions())
        println(machine.analyzeAndResolve(project.methods.find { it.metName == "Max2Improved" }!!))
    }
}