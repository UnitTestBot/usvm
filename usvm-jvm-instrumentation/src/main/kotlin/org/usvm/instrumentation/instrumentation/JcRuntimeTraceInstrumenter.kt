package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.isEnum
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.org.usvm.instrumentation.instrumentation.TraceHelper
import org.usvm.instrumentation.util.isSameSignature
import org.usvm.instrumentation.util.replace

/**
 * Class for runtime instrumentation for jcdb instructions
 * Collecting trace and information about static access
 */
class JcRuntimeTraceInstrumenter(
    override val jcClasspath: JcClasspath
) : JcInstrumenter, AbstractFullRawExprSetCollector() {

    private val rawStaticsGet = hashSetOf<JcRawFieldRef>()
    private val rawStaticsSet = hashSetOf<JcRawFieldRef>()

    private val traceHelper = TraceHelper(jcClasspath, TraceCollector::class.java)
    private val coveredInstructionMethodName = "jcInstructionCovered"
    private val staticFieldAccessedMethodName = "jcStaticFieldAccessed"

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
            val invocation = traceHelper.createTraceMethodCall(encodedInst, coveredInstructionMethodName)
            instrumentedJcInstructionsList.insertBefore(rawJcInstructionsList[i], invocation)

            getStaticFieldRefs(rawJcInstructionsList[i])
            rawStaticsSet.forEach { jcRawFieldRef ->
                val encodedRef = JcInstructionTracer.encodeStaticFieldAccess(
                    jcRawFieldRef, StaticFieldAccessType.SET, jcClasspath
                )
                val traceMethodCall = traceHelper.createTraceMethodCall(encodedRef, staticFieldAccessedMethodName)
                instrumentedJcInstructionsList.insertBefore(rawJcInstructionsList[i], traceMethodCall)
            }
            rawStaticsGet.forEach { jcRawFieldRef ->
                val encodedRef = JcInstructionTracer.encodeStaticFieldAccess(
                    jcRawFieldRef, StaticFieldAccessType.GET, jcClasspath
                )
                val traceMethodCall = traceHelper.createTraceMethodCall(encodedRef, staticFieldAccessedMethodName)
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