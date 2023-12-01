package org.usvm.instrumentation.testcase.descriptor

import com.jetbrains.rd.util.reactive.RdFault
import kotlinx.coroutines.TimeoutCancellationException
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.usvm.instrumentation.testcase.api.UTestExecutionFailedResult
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionTimedOutResult
import java.util.concurrent.CancellationException

class UTestUnexpectedExecutionBuilder(
    jcClasspath: JcClasspath
) {

    private val exceptionType = jcClasspath.findClass<Exception>().toType()

    fun build(exception: Exception): UTestExecutionResult {
        return when (exception) {
            is TimeoutCancellationException ->
                UTestExecutionFailedResult(buildExceptionDescriptor(exception.message ?: "timeout"))
            is CancellationException ->
                UTestExecutionFailedResult(buildExceptionDescriptor("CancellationException"))
            is RdFault ->
                UTestExecutionFailedResult(buildExceptionDescriptor(exception.reasonAsText))
            else ->
                error("Unexpected exception $exception")
        }
    }

    private fun buildExceptionDescriptor(msg: String) =
        UTestExceptionDescriptor(
            type = exceptionType,
            message = msg,
            stackTrace = listOf(),
            raisedByUserCode = false
        )
}