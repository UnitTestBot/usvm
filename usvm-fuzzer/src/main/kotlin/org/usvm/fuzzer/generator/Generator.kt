package org.usvm.fuzzer.generator

import org.jacodb.api.ext.objectType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.FuzzingContext
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestNullExpression
import org.usvm.instrumentation.util.executeWithSoftAndHardTimeouts
import org.usvm.instrumentation.util.executeWithTimeout
import java.util.concurrent.TimeoutException

abstract class Generator {
    lateinit var ctx: GeneratorContext
    protected abstract val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation?

    companion object {
        var objectGenerationStartTimeInMillis: Long? = null
        var softTimeout = GeneratorSettings.softTimeoutForObjectGeneration.inWholeMilliseconds
    }

    fun generate(depth: Int) =
        run {
            if (depth > GeneratorSettings.maxDepthOfObjectGeneration) {
                println("MAX DEPTH OF GENERATION REACHED")
                UTestValueRepresentation(UTestNullExpression(ctx.jcClasspath.objectType))
            } else if (System.currentTimeMillis() - objectGenerationStartTimeInMillis!! > softTimeout) {
                println("Soft timeout reached! Return null")
                UTestValueRepresentation(UTestNullExpression(ctx.jcClasspath.objectType))
            } else {
//                println("GENERATION ${this::class.java.name} depth $depth")
                generationFun.invoke(ctx, depth + 1).let {
                    if (it == null) {
                        println()
                    }
                    it!!
                }.also {
//                    println("GENERATION FINISHED ${this::class.java.name} depth $depth")
                }
            }
        }

    fun generate() =
        try {
            objectGenerationStartTimeInMillis = System.currentTimeMillis()
            val res = executeWithTimeout(GeneratorSettings.hardTimeoutForObjectGeneration.inWholeMilliseconds) {
                generate(0)
            }
            when (res) {
                is Throwable -> throw res
                is UTestValueRepresentation -> res
                else -> error("Unexpected result of type ${res!!::class.java.name} from generator")
            }
        } catch (e: TimeoutException) {
            println("HARD TIMEOUT REACHED!! RETURN NULL FOR WHOLE OBJECT")
            UTestValueRepresentation(UTestNullExpression(ctx.jcClasspath.objectType))
        }

}