package org.usvm.instrumentation.util

import org.jacodb.api.jvm.cfg.JcInst
import java.io.PrintStream

object TracePrinter {

    fun printTraceToConsole(trace: List<JcInst>, printStream: PrintStream = System.out) =
        printStream.println(buildString {
            val maxLengthInstruction = trace.maxOf { it.toString().length }
            val maxLengthMethod = trace.maxOf { it.location.method.name.length + it.location.method.description.length }
            val maxLengthClass = trace.maxOf { it.location.method.enclosingClass.name.length }
            for (element in trace) {
                val inst = element.toString().padStart(maxLengthInstruction)
                val method = "${element.location.method.name}${element.location.method.description}".padStart(maxLengthMethod)
                val cl = element.location.method.enclosingClass.name.padStart(maxLengthClass)
                appendLine("$inst | $method | $cl")
            }
        })
}