package org.usvm.instrumentation.executor

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.instrumentation.generated.models.ClassToId
import kotlin.math.pow

class TraceDeserializer(private val jcClasspath: JcClasspath) {
    private val deserializedInstructionsCache = HashMap<Long, JcInst>()
    private val deserializedClassesCache = HashMap<Long, JcClassOrInterface>()

    fun deserializeTrace(trace: List<Long>, coveredClasses: List<ClassToId>): List<JcInst> =
        trace.map { encodedInst ->
            deserializedInstructionsCache.getOrPut(encodedInst) {
                val classIdOffset = (2.0.pow(Byte.SIZE_BITS * 3).toLong() - 1) shl (Byte.SIZE_BITS * 5 - 1)
                val classId = encodedInst and classIdOffset shr (Byte.SIZE_BITS * 5)
                val methodIdOffset = (2.0.pow(Byte.SIZE_BITS * 2).toLong() - 1) shl (Byte.SIZE_BITS * 3 - 1)
                val methodId = encodedInst and methodIdOffset shr (Byte.SIZE_BITS * 3)
                val instructionId = (encodedInst and (2.0.pow(Byte.SIZE_BITS * 3).toLong() - 1)).toInt()
                val jcClass =
                    deserializedClassesCache.getOrPut(classId) {
                        val className = coveredClasses.find { it.classId == classId }
                            ?: error("Deserialization error")
                        jcClasspath.findClassOrNull(className.className) ?: error("Deserialization error")
                    }
                val jcMethod = jcClass.declaredMethods.sortedBy { it.description }[methodId.toInt()]
                jcMethod.instList
                    .find { it.location.index == instructionId }
                    ?: error("Deserialization error")
            }
        }
}