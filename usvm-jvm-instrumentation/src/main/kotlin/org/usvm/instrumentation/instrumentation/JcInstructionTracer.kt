package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcRawFieldRef
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.util.enclosingClass
import org.usvm.instrumentation.util.enclosingMethod
import org.usvm.instrumentation.util.toJcClassOrInterface

//Jacodb instructions tracer
object JcInstructionTracer : Tracer<Trace> {

    // We are instrumenting statics access to build descriptors only for accessed while execution statics
    enum class StaticFieldAccessType {
        GET, SET
    }

    override fun getTrace(): Trace {
        val trace = List(TraceCollector.trace.size) { idx -> decode(
            TraceCollector.trace.arr[idx]) }
        val statics = List(TraceCollector.statics.size) { idx -> decodeStatic(
            TraceCollector.statics.arr[idx]) }
        return Trace(trace, statics)
    }

    class EncodedClass(val id: Long) {
        val encodedMethods = hashMapOf<JcMethod, EncodedMethod>()
        val encodedFields = hashMapOf<JcField, EncodedField>()
        var currentMethodIndex = 0L
        var currentFieldIndex = 0L
    }

    class EncodedMethod(val id: Long) {
        val encodedInstructions = hashMapOf<JcInst, EncodedInst>()
        var currentInstIndex = 0L
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

    private fun encodeClass(jcClass: JcClassOrInterface) = encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }
    private fun encodeMethod(jcClass: JcClassOrInterface, jcMethod: JcMethod): EncodedMethod {
        val encodedClass = encodeClass(jcClass)
        return encodedClass.encodedMethods.getOrPut(jcMethod) { EncodedMethod(encodedClass.currentMethodIndex++) }
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
            encodedMethod.encodedInstructions.getOrPut(jcInst) { EncodedInst(encodedMethod.currentInstIndex++) }
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
        val jcClass =
            jcRawFieldRef.declaringClass.toJcClassOrInterface(jcClasspath) ?: error("Can't find class in classpath")
        val encodedClass = encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }
        val indexedJcField =
            jcClass.declaredFields.withIndex()
                .find { it.value.isStatic && it.value.name == jcRawFieldRef.fieldName }
                ?: error("Field not found")
        val accessTypeId = accessType.ordinal.toLong()
        val instId = encodeStaticFieldAccessId(encodedClass.id, indexedJcField.index.toLong(), accessTypeId)
        encodedJcStaticFieldRef[instId] = indexedJcField.value to accessType
        return instId
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