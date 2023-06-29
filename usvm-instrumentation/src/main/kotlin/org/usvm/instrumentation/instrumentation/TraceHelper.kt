package org.usvm.instrumentation.org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcRawCallInst
import org.jacodb.api.cfg.JcRawStaticCallExpr
import org.jacodb.api.cfg.JcRawValue
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectType
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
        JcVirtualClassImpl(globalObjectJClass.name,
            globalObjectJClass.modifiers,
            listOf(),
            globalObjectJClass.declaredMethods.map { createJcVirtualMethod(it) }).also {
            it.classpath = jcClasspath
        }

    //We need virtual method to insert it invocation in instrumented instruction list
    private fun createJcVirtualMethod(jMethod: Method): JcVirtualMethod = JcVirtualMethodImpl(
        jMethod.name, jMethod.modifiers, TypeNameImpl(jMethod.returnType.name), createJcVirtualMethodParams(jMethod), ""
    )

    private fun createJcVirtualMethodParams(jMethod: Method): List<JcVirtualParameter> =
        jMethod.parameters.mapIndexed { i, p -> JcVirtualParameter(i, TypeNameImpl(p.type.typeName)) }

    /**
     * This method create instrumenting method call to insert it in instruction list
     * @param jcInstId --- Encoded instruction (see JcInstructionTracer.encode)
     * @param jcTraceMethod --- virtual jacodb method for instrumenting
     */
    fun createTraceMethodCall(jcInstId: Long, traceMethodName: String): JcRawCallInst {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == traceMethodName }!!
        return JcRawCallInst(jcTraceMethod, createStaticExprWithLongArg(jcInstId, jcTraceMethod))
    }

    fun createMockCollectorCall(traceMethodName: String, id: Long, jcThisReference: JcRawValue): JcRawStaticCallExpr {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == traceMethodName }!!
        val jcRawLong = JcRawLong(id)
        return JcRawStaticCallExpr(
            jcVirtualGlobalObjectClass.typename,
            jcTraceMethod.name,
            listOf(jcClasspath.long.getTypename(), jcClasspath.objectType.getTypename()),
            jcTraceMethod.returnType,
            listOf(jcRawLong, jcThisReference)
        )
    }

    fun createStaticExprWithLongArg(arg: Long, jcTraceMethod: JcVirtualMethod): JcRawStaticCallExpr {
        val argAsJcConst = JcRawLong(arg)
        return JcRawStaticCallExpr(
            jcVirtualGlobalObjectClass.typename,
            jcTraceMethod.name,
            listOf(jcClasspath.long.getTypename()),
            jcTraceMethod.returnType,
            listOf(argAsJcConst)
        )
    }


}