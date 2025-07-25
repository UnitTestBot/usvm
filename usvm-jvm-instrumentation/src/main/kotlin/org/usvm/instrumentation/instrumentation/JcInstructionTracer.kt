package org.usvm.instrumentation.instrumentation

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.jvm.util.enclosingClass
import org.usvm.jvm.util.enclosingMethod
import org.usvm.jvm.util.toJcClassOrInterface

//Jacodb instructions tracer
object JcInstructionTracer : Tracer<Trace> {

    // We are instrumenting statics access to build descriptors only for accessed while execution statics
    enum class StaticFieldAccessType {
        GET, SET
    }

    override fun getTrace(): Trace {
        val traceFromTraceCollector =
            TraceCollector.trace.allValues
        val trace = List(traceFromTraceCollector.size) { idx ->
            decode(traceFromTraceCollector[idx])
        }
        val statics = List(TraceCollector.statics.size) { idx ->
            decodeStatic(TraceCollector.statics.arr[idx])
        }
        return Trace(trace, statics)
    }

    fun coveredInstructionsIds(): List<Long> {
        val traceFromTraceCollector =
            TraceCollector.trace.allValues
        return List(traceFromTraceCollector.size) { idx -> traceFromTraceCollector[idx] }
    }

    fun getEncodedClasses() =
        encodedClasses.entries.associate { it.key to it.value.id }

    class EncodedClass(val id: Long) {
        val encodedMethods = hashMapOf<JcMethod, EncodedMethod>()
        val encodedFields = hashMapOf<JcField, EncodedField>()
        var currentFieldIndex = 0L
    }

    class EncodedMethod(val id: Long) {
        val encodedInstructions = hashMapOf<JcInst, EncodedInst>()
    }

    class EncodedField(val id: Long)

    class EncodedInst(val id: Long)

    private val encodedJcInstructions = hashMapOf<Long, JcInst>()
    private val encodedJcStaticFieldRef = hashMapOf<Long, Pair<JcField, StaticFieldAccessType>>()

    private val encodedClasses = hashMapOf<JcClassOrInterface, EncodedClass>()
    private var currentClassIndex = 0L

    /**
     *  0000 0000 0000 0000 0000 0000 0000 0000
     * |  class id    |methodId|    instId    |
     */
    private fun encodeTraceId(classId: Long, methodId: Long, instId: Long): Long {
        return (classId shl Byte.SIZE_BITS * 5) or (methodId shl Byte.SIZE_BITS * 3) or instId
    }

    private fun encodeClass(jcClass: JcClassOrInterface) =
        encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }

    private fun encodeMethod(jcClass: JcClassOrInterface, jcMethod: JcMethod): EncodedMethod {
        val encodedClass = encodeClass(jcClass)
        val methodIndex = jcClass.declaredMethods
            .sortedBy { it.description }
            .indexOf(jcMethod)
            .also { if (it == -1) error("Encoding error") }
        return encodedClass.encodedMethods.getOrPut(jcMethod) { EncodedMethod(methodIndex.toLong()) }
    }

    fun encodeField(jcClass: JcClassOrInterface, jcField: JcField): EncodedField {
        val encodedClass = encodeClass(jcClass)
        return encodedClass.encodedFields.getOrPut(jcField) { EncodedField(encodedClass.currentFieldIndex++) }
    }

    fun encode(jcInst: JcInst): Long {
        val jcClass = jcInst.enclosingClass
        val jcMethod = jcInst.enclosingMethod
        val encodedClass = encodeClass(jcClass)
        val encodedMethod = encodeMethod(jcClass, jcMethod)
        val encodedInst =
            encodedMethod.encodedInstructions.getOrPut(jcInst) {
                EncodedInst(jcInst.location.index.toLong())
            }
        val instId = encodeTraceId(encodedClass.id, encodedMethod.id, encodedInst.id)
        encodedJcInstructions[instId] = jcInst
        return instId
    }

    fun encode(jcMethod: JcMethod): Long {
        val jcClass = jcMethod.enclosingClass
        val encodedClass = encodeClass(jcClass)
        val encodedMethod = encodeMethod(jcClass, jcMethod)
        return encodeTraceId(encodedClass.id, encodedMethod.id, 0L)
    }

    /**
     *  0000 0000 0000 0000 0000 0000 0000 0000
     * |  class id    |fieldId | accessTypeId |
     */
    private fun encodeStaticFieldAccessId(classId: Long, fieldId: Long, accessTypeId: Long): Long {
        return (classId shl Byte.SIZE_BITS * 5) or (fieldId shl Byte.SIZE_BITS * 3) or accessTypeId
    }

    fun encodeStaticFieldAccess(
        jcRawFieldRef: JcRawFieldRef,
        accessType: StaticFieldAccessType,
        jcClasspath: JcClasspath
    ): Long {
        var jcClass =
            jcRawFieldRef.declaringClass.toJcClassOrInterface(jcClasspath) ?: error("Can't find class in classpath")
        while (true) {
            val encodedClass = encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }
            val indexedJcField =
                jcClass.declaredFields.withIndex()
                    .find { it.value.isStatic && it.value.name == jcRawFieldRef.fieldName }
            if (indexedJcField == null) {
                // static fields can be accessed via subclass of declaring class
                jcClass = jcClass.superClass
                    ?: error("Field `${jcRawFieldRef.declaringClass.typeName}.${jcRawFieldRef.fieldName}` not found")
                continue
            }
            val accessTypeId = accessType.ordinal.toLong()
            val instId = encodeStaticFieldAccessId(encodedClass.id, indexedJcField.index.toLong(), accessTypeId)
            encodedJcStaticFieldRef[instId] = indexedJcField.value to accessType
            return instId
        }
    }

    private fun decode(jcInstructionId: Long): JcInst =
        encodedJcInstructions[jcInstructionId] ?: error("Can't decode inst")

    private fun decodeStatic(jcStaticId: Long): Pair<JcField, StaticFieldAccessType> =
        encodedJcStaticFieldRef[jcStaticId] ?: error("Can't decode inst")

    override fun reset() {
        TraceCollector.trace.clear()
        TraceCollector.statics.clear()
    }

}

data class Trace(val trace: List<JcInst>, val statics: List<Pair<JcField, StaticFieldAccessType>>)