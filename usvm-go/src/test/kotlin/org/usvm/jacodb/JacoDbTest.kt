package org.usvm.jacodb

import org.junit.jupiter.api.Test
import org.usvm.generated.StartDeserializer
import org.usvm.generated.ssa_Program
import java.io.File
import kotlin.system.measureTimeMillis

class JacoDbTest {
    @Test
    fun testMax2() {
        var jcdb: GoProject

        val buffReader = File("./src/main/kotlin/org/usvm/generated/filled.txt").bufferedReader()

        val stopwatch = measureTimeMillis {
            val res = StartDeserializer(buffReader) as ssa_Program

            jcdb = res.createJacoDBProject()
        }

        println(stopwatch)


    }
}