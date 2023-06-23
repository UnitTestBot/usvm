package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.isEnum
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
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.typename
import org.usvm.instrumentation.trace.collector.TraceCollector
import org.usvm.instrumentation.util.isSameSignature
import org.usvm.instrumentation.util.replace
import java.lang.reflect.Method

/**
 * Class for runtime instrumentation for jcdb instructions
 * Collecting trace and information about static access
 */
class JcRuntimeTraceInstrumenter(
    override val jcClasspath: JcClasspath
) : JcInstrumenter, AbstractFullRawExprSetCollector() {

    private val rawStaticsGet = hashSetOf<JcRawFieldRef>()
    private val rawStaticsSet = hashSetOf<JcRawFieldRef>()


    private val traceCollectorClass = TraceCollector::class.java

    //JcClass for trace collector
    private val jcCollectorClass = JcVirtualClassImpl(traceCollectorClass.name,
        traceCollectorClass.modifiers,
        listOf(),
        traceCollectorClass.declaredMethods.map { createJcVirtualMethod(it) }).also {
        it.classpath = jcClasspath
    }

    //JcMethod for instrumenting jacodb instructions. See TraceCollector.jcInstructionCovered
    private val coveredJcInstructionMethod = jcCollectorClass.declaredMethods.find { it.name == "jcInstructionCovered" }
        ?: error("Can't find method in trace collector")

    //JcMethod for instrumenting statics. See TraceCollector.jcInstructionCovered
    private val accessJcStaticFieldMethod = jcCollectorClass.declaredMethods.find { it.name == "jcStaticFieldAccessed" }
        ?: error("Can't find method in trace collector")

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
    private fun createTraceMethodCall(jcInstId: Long, jcTraceMethod: JcVirtualMethod): JcRawCallInst {
        val jcInstIdAsLongConst = JcRawLong(jcInstId)
        val staticCallExpr = JcRawStaticCallExpr(
            jcCollectorClass.typename,
            jcTraceMethod.name,
            listOf(jcClasspath.long.getTypename()),
            jcClasspath.void.getTypename(),
            listOf(jcInstIdAsLongConst)
        )
        return JcRawCallInst(jcTraceMethod, staticCallExpr)
    }

    override fun visitJcRawAssignInst(inst: JcRawAssignInst) {
        val lhv = inst.lhv
        if (lhv is JcRawFieldRef && lhv.instance == null) {
            rawStaticsSet.add(lhv)
        }
        inst.rhv.accept(this)
    }

    private fun getStaticFieldRefs(inst: JcRawInst) {
        rawStaticsGet.clear()
        rawStaticsSet.clear()
        inst.accept(this)
    }

    override fun ifMatches(expr: JcRawExpr) {
        if (expr is JcRawFieldRef && expr.instance == null) rawStaticsGet.add(expr)
    }

    private fun instrumentMethod(jcMethod: JcMethod): MethodNode {
        val rawJcInstructionsList = jcMethod.rawInstList.filter { it !is JcRawLabelInst && it !is JcRawLineNumberInst }
        val jcInstructionsList = jcMethod.instList
        val instrumentedJcInstructionsList = jcMethod.rawInstList.toMutableList()
        for (i in jcInstructionsList.indices) {
            val encodedInst = JcInstructionTracer.encode(jcInstructionsList[i])
            val invocation = createTraceMethodCall(encodedInst, coveredJcInstructionMethod)
            instrumentedJcInstructionsList.insertBefore(rawJcInstructionsList[i], invocation)

            getStaticFieldRefs(rawJcInstructionsList[i])
            rawStaticsSet.forEach { jcRawFieldRef ->
                val encodedRef = JcInstructionTracer.encodeStaticFieldAccess(
                    jcRawFieldRef, StaticFieldAccessType.SET, jcClasspath
                )
                val traceMethodCall = createTraceMethodCall(encodedRef, accessJcStaticFieldMethod)
                instrumentedJcInstructionsList.insertBefore(rawJcInstructionsList[i], traceMethodCall)
            }
            rawStaticsGet.forEach { jcRawFieldRef ->
                val encodedRef = JcInstructionTracer.encodeStaticFieldAccess(
                    jcRawFieldRef, StaticFieldAccessType.GET, jcClasspath
                )
                val traceMethodCall = createTraceMethodCall(encodedRef, accessJcStaticFieldMethod)
                instrumentedJcInstructionsList.insertBefore(rawJcInstructionsList[i], traceMethodCall)
            }
        }
        return MethodNodeBuilder(jcMethod, instrumentedJcInstructionsList).build()
    }

    override fun instrumentClass(classNode: ClassNode): ClassNode {
        val className = classNode.name.replace('/', '.')
        val jcClass = jcClasspath.findClassOrNull(className) ?: return classNode
        val asmMethods = classNode.methods
        val methodsToInstrument = if (jcClass.isEnum) {
            jcClass.declaredMethods.filterNot { it.isConstructor || it.isClassInitializer || it.name == "values" || it.name == "valueOf" }
        } else {
            jcClass.declaredMethods.filterNot { it.isConstructor || it.isClassInitializer }
        }
        //Copy of clinit method to be able to rollback statics between executions!
        //We are not able to call <clinit> method directly with reflection
        asmMethods.find { it.name == "<clinit>" }?.let { clinitNode ->
            val clinitCopy = MethodNode(9, "generatedClinit0", "()V", null, emptyArray())
            clinitNode.instructions.forEach { clinitCopy.instructions.add(it) }
            asmMethods.add(0, clinitCopy)
        }
        methodsToInstrument.forEach { jcMethod ->
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: return@forEach
            val tracedMethod = instrumentMethod(jcMethod)
            asmMethods.replace(asmMethod, tracedMethod)
        }
        return classNode
    }

}