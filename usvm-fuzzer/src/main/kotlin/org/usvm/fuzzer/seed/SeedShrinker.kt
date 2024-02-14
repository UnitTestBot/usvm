package org.usvm.fuzzer.seed

import org.jacodb.api.JcField
import org.usvm.fuzzer.api.UTypedTestInst
import org.usvm.fuzzer.util.getTrace
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.testcase.api.UTestExecutionExceptionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


class SeedShrinker(
    private val executor: UTestConcreteExecutor,
) {

    suspend fun shrink(seed: Seed): Seed? {
        val minimizedArgsInitialExpressions =
            seed.args.map { arg ->
                minimize(arg.initialExprs, seed, arg)
            }
        val newArgs = seed.args.zip(minimizedArgsInitialExpressions).map {
            Seed.ArgumentDescriptor(it.first.instance, it.first.type, it.second)
        }
        val newSeed = Seed(
            seed.targetMethod,
            newArgs,
            seed.argsChoosingStrategy,
        )
        return newSeed
    }

    private suspend fun minimize(input: List<UTypedTestInst>, seed: Seed, arg: Seed.ArgumentDescriptor): List<UTypedTestInst> {
        val resInput = input.toMutableList()
        var ind = 0
        while (ind < resInput.size) {
            val removedUTest = resInput.removeAt(ind)
            if (!isPassing(resInput, seed, arg)) {
                resInput.add(ind, removedUTest)
                ind++
            }
        }
        return resInput
    }

    private suspend fun isPassing(reducedInput: List<UTypedTestInst>, seed: Seed, arg: Seed.ArgumentDescriptor): Boolean {
        val newArg = Seed.ArgumentDescriptor(arg.instance, arg.type, reducedInput)
        val mutatedSeed = seed.mutate(arg, newArg)
        val execRes = executor.executeAsync(mutatedSeed.toUTest())
        return execRes::class.java == seed.executionResultType && areDescriptorsEquals(seed, execRes)
    }

    private fun areDescriptorsEquals(seed: Seed, execRes: UTestExecutionResult): Boolean {
        val initialDescriptors1 = seed.argsInitialDescriptors
        val initialDescriptors2 = execRes.getInitialDescriptors()
        if (initialDescriptors1 == null && initialDescriptors2 != null) return false
        if (initialDescriptors1 != null && initialDescriptors2 == null) return false
        if (initialDescriptors1 != null && initialDescriptors2 != null) {
            val allSame = initialDescriptors1.zip(initialDescriptors2).all { (d1, d2) ->
                if (d1 == null && d2 != null) return false
                if (d1 != null && d2 == null) return false
                if (d1 == null && d2 == null) return true
                try {
                    d1!!.areEqual(d2!!)
                }catch (e: Throwable) {
                    println()
                    throw e
                }
            }
            if (!allSame) return false
        }
        //TODO!! Handle statics
        return true
    }

    private fun UTestExecutionResult.getInitialDescriptors(): List<UTestValueDescriptor?>? =
        when (this) {
            is UTestExecutionSuccessResult -> this.initialState.let { listOf(it.instanceDescriptor) + it.argsDescriptors }
            is UTestExecutionExceptionResult -> this.initialState.let { listOf(it.instanceDescriptor) + it.argsDescriptors }
            else -> null
        }

    private fun UTestExecutionResult.getStaticDescriptors(): Map<JcField, UTestValueDescriptor>? =
        when (this) {
            is UTestExecutionSuccessResult -> this.initialState.statics
            is UTestExecutionExceptionResult -> this.initialState.statics
            else -> null
        }



}