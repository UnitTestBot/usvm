package org.usvm.instrumentation.util

import org.usvm.jvm.util.JcExecutor
import org.usvm.jvm.util.withAccessibility
import java.lang.reflect.Constructor
import java.lang.reflect.Method

fun Method.invokeWithAccessibility(instance: Any?, args: List<Any?>, executor: JcExecutor): Any? =
    executeWithTimeout(executor) {
        withAccessibility {
            invoke(instance, *args.toTypedArray())
        }
    }

fun Constructor<*>.newInstanceWithAccessibility(args: List<Any?>, executor: JcExecutor): Any =
    executeWithTimeout(executor) {
        withAccessibility {
            newInstance(*args.toTypedArray())
        }
    } ?: error("Cant instantiate class ${this.declaringClass.name}")

fun executeWithTimeout(executor: JcExecutor, body: () -> Any?): Any? {
    val timeout = InstrumentationModuleConstants.methodExecutionTimeout
    val (result, exception) = executor.executeWithResult(timeout, body)
    if (exception != null)
        throw exception

    return result
}
