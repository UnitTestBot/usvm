package org.usvm.instrumentation.jacodb.transform

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcRawCallInst
import org.jacodb.api.cfg.JcRawLabelInst
import org.jacodb.api.cfg.JcRawLineNumberInst
import org.jacodb.api.cfg.JcRawStaticCallExpr
import org.jacodb.api.ext.long
import org.jacodb.api.ext.void
import org.jacodb.impl.cfg.JcRawLong
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.usvm.instrumentation.jacodb.util.getTypename
import org.usvm.instrumentation.jacodb.util.typename
import org.usvm.instrumentation.trace.collector.TraceCollector
import org.usvm.instrumentation.util.isSameSignature
import org.usvm.instrumentation.util.replace
import java.lang.reflect.Method

class JcRuntimeTraceInstrumenter(
    override val jcClasspath: JcClasspath
): JcInstrumenter {

    private val traceCollectorClass = TraceCollector::class.java

    private val jcCollectorClass =
        JcVirtualClassImpl(
            traceCollectorClass.name,
            traceCollectorClass.modifiers,
            listOf(),
            traceCollectorClass.declaredMethods.map { createJcVirtualMethod(it) }
        ).also {
            it.classpath = jcClasspath
        }

    private val coveredJcInstructionMethod =
        jcCollectorClass.declaredMethods.find { it.name == "jcInstructionCovered" }!!


    private fun createJcVirtualMethod(jMethod: Method): JcVirtualMethod =
        JcVirtualMethodImpl(
            jMethod.name,
            jMethod.modifiers,
            TypeNameImpl(jMethod.returnType.name),
            createJcVirtualMethodParams(jMethod),
            ""
        )

    private fun createJcVirtualMethodParams(jMethod: Method): List<JcVirtualParameter> =
        jMethod.parameters.mapIndexed { i, p -> JcVirtualParameter(i, TypeNameImpl(p.type.typeName)) }


    private fun createCoveredInstructionCall(jcInstId: Long): JcRawCallInst {
        val jcInstIdAsLongConst = JcRawLong(jcInstId)
        val staticCallExpr = JcRawStaticCallExpr(
            jcCollectorClass.typename,
            coveredJcInstructionMethod.name,
            listOf(jcClasspath.long.getTypename()),
            jcClasspath.void.getTypename(),
            listOf(jcInstIdAsLongConst)
        )
        return JcRawCallInst(coveredJcInstructionMethod, staticCallExpr)
    }

    private fun instrumentMethod(jcMethod: JcMethod): MethodNode {
        val rawJcInstructionsList = jcMethod.rawInstList.filter { it !is JcRawLabelInst && it !is JcRawLineNumberInst }
        val jcInstructionsList = jcMethod.instList
        val instrumentedJcInstructionsList = jcMethod.rawInstList.toMutableList()
        for (i in jcInstructionsList.indices) {
            val encodedInst = JcInstructionTracer.encode(jcInstructionsList[i])
            val invocation = createCoveredInstructionCall(encodedInst)
            instrumentedJcInstructionsList.insertBefore(rawJcInstructionsList[i], invocation)
        }
        return MethodNodeBuilder(jcMethod, instrumentedJcInstructionsList).build()
    }

    override fun instrumentClass(classNode: ClassNode): ClassNode {
        val className = classNode.name.replace('/', '.')
        val jcClass = jcClasspath.findClassOrNull(className) ?: return classNode
        val asmMethods = classNode.methods
        jcClass.declaredMethods.filter { !it.isConstructor }.forEach { jcMethod ->
            val asmMethod = asmMethods.find { jcMethod.body().isSameSignature(it) } ?: return@forEach
            val tracedMethod = instrumentMethod(jcMethod)
            asmMethods.replace(asmMethod, tracedMethod)
        }
        return classNode
    }

}