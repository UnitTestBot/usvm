package org.usvm.api.util

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.usvm.UExpr
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.api.util.JcTestStateResolver.ResolveMode.CURRENT
import org.usvm.api.util.JcTestStateResolver.ResolveMode.MODEL
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase

/**
 * A class, responsible for resolving a single [JcTest] for a specific method from a symbolic state.
 *
 * Uses reflection to resolve objects.
 *
 * @param classLoader a class loader to load target classes.
 */
class JcTestInterpreter(
    private val classLoader: ClassLoader = JcClassLoader,
) : JcTestResolver {
    /**
     * Resolves a [JcTest] from a [method] from a [state].
     */
    override fun resolve(method: JcTypedMethod, state: JcState): JcTest {
        val model = state.models.first()
        val memory = state.memory

        val ctx = state.ctx

        // Note that we cannot use the same MemoryScope due to their caches
        val beforeMemoryScope = MemoryScope(ctx, model, memory, method, classLoader)
        val afterMemoryScope = MemoryScope(ctx, model, memory, method, classLoader)

        val before = beforeMemoryScope.withMode(MODEL) { (this as MemoryScope).resolveState() }
        val after = afterMemoryScope.withMode(CURRENT) { (this as MemoryScope).resolveState() }

        val result = when (val res = state.methodResult) {
            is JcMethodResult.NoCall -> error("No result found")
            is JcMethodResult.Success -> Result.success(
                afterMemoryScope.withMode(CURRENT) {
                    resolveExpr(res.value, method.returnType)
                }
            )
            is JcMethodResult.JcException -> Result.failure(resolveException(res, afterMemoryScope))
        }
        val coverage = resolveCoverage(method, state)

        return JcTest(method, before, after, result, coverage)
    }

    private fun resolveException(
        exception: JcMethodResult.JcException,
        afterMemory: MemoryScope
    ): Throwable = afterMemory.withMode(CURRENT) {
        resolveExpr(exception.address, exception.type) as Throwable
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveCoverage(method: JcTypedMethod, state: JcState): JcCoverage {
        // TODO: extract coverage
        return JcCoverage(emptyMap())
    }

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param finalStateMemory a read-only memory to read [ULValue]s from.
     * @param classLoader a class loader to load target classes.
     */
    private class MemoryScope(
        ctx: JcContext,
        model: UModelBase<JcType>,
        finalStateMemory: UReadOnlyMemory<JcType>,
        method: JcTypedMethod,
        private val classLoader: ClassLoader = JcClassLoader,
    ) : JcTestStateResolver<Any?>(ctx, model, finalStateMemory, method) {
        override val decoderApi: JcTestInterpreterDecoderApi = JcTestInterpreterDecoderApi(ctx, classLoader)

        fun resolveState(): JcParametersState {
            val thisInstance = resolveThisInstance()
            val parameters = resolveParameters()

            resolveStatics()

            return JcParametersState(thisInstance, parameters, decoderApi.staticFields)
        }

        override fun allocateClassInstance(type: JcClassType): Any =
            type.allocateInstance(classLoader)

        override fun allocateString(value: Any?): Any = when (value) {
            is CharArray -> String(value)
            is ByteArray -> String(value)
            else -> String()
        }
    }
}
