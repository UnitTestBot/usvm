package org.usvm.instrumentation.instrumentation

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.api.jvm.cfg.JcRawValue
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.impl.cfg.JcRawLong
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.typename
import java.lang.reflect.Method

class TraceHelper(
    private val jcClasspath: JcClasspath,
    globalObjectJClass: Class<*>
) {

    private val jcVirtualGlobalObjectClass =
        JcVirtualClassImpl(
            name = globalObjectJClass.name,
            access = globalObjectJClass.modifiers,
            initialFields = listOf(),
            initialMethods = globalObjectJClass.declaredMethods.map { createJcVirtualMethod(it) }
        ).also { it.classpath = jcClasspath }

    //We need virtual method to insert it invocation in instrumented instruction list
    private fun createJcVirtualMethod(jMethod: Method): JcVirtualMethod = JcVirtualMethodImpl(
        name = jMethod.name,
        access = jMethod.modifiers,
        returnType = TypeNameImpl(jMethod.returnType.name),
        parameters = createJcVirtualMethodParams(jMethod),
        description = ""
    )

    private fun createJcVirtualMethodParams(jMethod: Method): List<JcVirtualParameter> =
        jMethod.parameters.mapIndexed { i, p -> JcVirtualParameter(i, TypeNameImpl(p.type.typeName)) }

    /**
     * This method create instrumenting method call to insert it in instruction list
     * @param jcInstId --- Encoded instruction (see JcInstructionTracer.encode)
     * @param traceMethodName --- jacodb method name for instrumenting
     */
    fun createTraceMethodCall(jcInstId: Long, traceMethodName: String): JcRawCallInst {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == traceMethodName }!!
        return JcRawCallInst(jcTraceMethod, createStaticExprWithLongArg(jcInstId, jcTraceMethod))
    }

    fun createMockCollectorCall(traceMethodName: String, id: Long, jcThisReference: JcRawValue): JcRawStaticCallExpr {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == traceMethodName }!!
        val jcRawLong = JcRawLong(id)
        return JcRawStaticCallExpr(
            declaringClass = jcVirtualGlobalObjectClass.typename,
            methodName = jcTraceMethod.name,
            argumentTypes = listOf(jcClasspath.long.getTypename(), jcClasspath.objectType.getTypename()),
            returnType = jcTraceMethod.returnType,
            args = listOf(jcRawLong, jcThisReference)
        )
    }

    fun createMockCollectorIsInExecutionCall(): JcRawStaticCallExpr {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == "isInExecution" }!!
        return JcRawStaticCallExpr(
            declaringClass = jcVirtualGlobalObjectClass.typename,
            methodName = jcTraceMethod.name,
            argumentTypes = listOf(),
            returnType = jcTraceMethod.returnType,
            args = listOf()
        )
    }

    fun createStaticExprWithLongArg(arg: Long, jcTraceMethod: JcVirtualMethod): JcRawStaticCallExpr {
        val argAsJcConst = JcRawLong(arg)
        return JcRawStaticCallExpr(
            declaringClass = jcVirtualGlobalObjectClass.typename,
            methodName = jcTraceMethod.name,
            argumentTypes = listOf(jcClasspath.long.getTypename()),
            returnType = jcTraceMethod.returnType,
            args = listOf(argAsJcConst)
        )
    }


}