package org.usvm.instrumentation.util

import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcInst

object TracePrinter {

    fun printTraceToConsole(trace: List<JcInst>) =
        println(buildString {
            var offset = 2
            var curMethod = trace.first().location.method
            for (i in 0 until trace.size - 1) {
                val jcInst = trace[i]
                if (jcInst.location.method != curMethod) {
                    offset -= 2
                    curMethod = jcInst.location.method
                }
                repeat(offset) { append("|") }
                append(" ")
                appendLine(jcInst)
                val callExpr = jcInst.operands.find { it is JcCallExpr } as? JcCallExpr
                if (callExpr != null || jcInst is JcCallInst) {
                    offset += 2; curMethod = trace[i + 1].location.method
                }
            }
        })
}