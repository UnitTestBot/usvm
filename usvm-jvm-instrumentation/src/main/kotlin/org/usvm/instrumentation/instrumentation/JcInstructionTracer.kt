package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcRawFieldRef
import org.jacodb.api.ext.fields
import org.jacodb.api.ext.methods
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.generated.models.TracedInstruction
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.FieldAccessType
import org.usvm.instrumentation.util.enclosingClass
import org.usvm.instrumentation.util.enclosingMethod
import org.usvm.instrumentation.util.toJcClassOrInterface

//Jacodb instructions tracer
object JcInstructionTracer : Tracer<Trace> {

    // We are instrumenting statics access to build descriptors only for accessed while execution statics
    enum class FieldAccessType {
        GET, SET
    }

    override fun getTrace(): Trace {
        val (traceFromTraceCollector, numberOfTouches) =
            TraceCollector.trace.allValues
        val trace = List(traceFromTraceCollector.size) { idx ->
            decode(traceFromTraceCollector[idx]) to numberOfTouches[idx]
        }.toMap()
        val accessedFieldsFromTraceCollector =
            TraceCollector.fieldsAccessed.allValues
        val accessedFields = List(accessedFieldsFromTraceCollector.size) { idx ->
            decodeFieldRef(accessedFieldsFromTraceCollector[idx])
        }
        return Trace(trace, accessedFields.filter { it.first.isStatic }, accessedFields.filter { !it.first.isStatic })
    }

    fun coveredInstructionsIds(): List<TracedInstruction> {
        val (traceFromTraceCollector, numberOfTouches) =
            TraceCollector.trace.allValues
        return List(traceFromTraceCollector.size) { idx ->
            TracedInstruction(traceFromTraceCollector[idx], numberOfTouches[idx]) }
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
    private val encodedJcFieldRef = hashMapOf<Long, Pair<JcField, FieldAccessType>>()

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
        val methodIndex = jcClass.methods
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
    private fun encodeFieldAccessId(classId: Long, fieldId: Long, accessTypeId: Long): Long {
        return (classId shl Byte.SIZE_BITS * 5) or (fieldId shl Byte.SIZE_BITS * 3) or accessTypeId
    }

    fun encodeFieldAccess(
        jcRawFieldRef: JcRawFieldRef,
        accessType: FieldAccessType,
        isStatic: Boolean,
        jcClasspath: JcClasspath
    ): Long {
        val jcClass =
            jcRawFieldRef.declaringClass.toJcClassOrInterface(jcClasspath) ?: error("Can't find class in classpath")
        val encodedClass = encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }
        val indexedJcField =
            (jcClass.declaredFields + jcClass.fields).withIndex()
                .find { (!isStatic || it.value.isStatic) && it.value.name == jcRawFieldRef.fieldName }
                ?: error("Field not found")
        val accessTypeId = accessType.ordinal.toLong()
        val instId = encodeFieldAccessId(encodedClass.id, indexedJcField.index.toLong(), accessTypeId)
        encodedJcFieldRef[instId] = indexedJcField.value to accessType
        return instId
    }

    private fun decode(jcInstructionId: Long): JcInst =
        encodedJcInstructions[jcInstructionId] ?: error("Can't decode inst")

    private fun decodeFieldRef(jcFieldId: Long): Pair<JcField, FieldAccessType> =
        encodedJcFieldRef[jcFieldId] ?: error("Can't decode inst")

    override fun reset() {
        TraceCollector.trace.clear()
        TraceCollector.fieldsAccessed.clear()
    }

}

data class Trace(val trace: Map<JcInst, Long>, val statics: List<Pair<JcField, FieldAccessType>>, val fields: List<Pair<JcField, FieldAccessType>>)