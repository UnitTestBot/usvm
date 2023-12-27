package org.usvm.instrumentation.testcase.descriptor

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.ext.enumValues
import org.jacodb.api.ext.isEnum
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.FieldAccessType
import org.usvm.instrumentation.util.*

class StaticDescriptorsBuilder(
    private var workerClassLoader: WorkerClassLoader,
    private val initialValue2DescriptorConverter: Value2DescriptorConverter
) {

    val builtInitialDescriptors = HashMap<JcField, UTestValueDescriptor?>()

    private val stateAfterStaticsDescriptors = HashMap<JcField, UTestValueDescriptor?>()
    private val descriptor2ValueConverter = Descriptor2ValueConverter(workerClassLoader)

    fun setClassLoader(workerClassLoader: WorkerClassLoader) {
        this.workerClassLoader = workerClassLoader
    }

    fun buildInitialDescriptorForClass(jcClass: JcClassOrInterface) {
        jcClass.allDeclaredFields
            .filter { it.needToBuildDescriptor() }
            .forEach { jcField ->
                builtInitialDescriptors.getOrPut(jcField) { buildDescriptor(jcField, initialValue2DescriptorConverter) }
            }
    }

    fun buildDescriptorsForExecutedStatics(
        fields: Set<Pair<JcField, FieldAccessType>>,
        resultValue2DescriptorConverter: Value2DescriptorConverter
    ): Result<Map<JcField, UTestValueDescriptor>> =
        runCatching {
            val descriptorMap = fields
                .map { it.first }
                .filter { it.needToBuildDescriptor() }
                .associateWith { buildDescriptor(it, resultValue2DescriptorConverter) }
            stateAfterStaticsDescriptors.putAll(descriptorMap)
            filterNullDescriptors(descriptorMap)
        }

    private fun filterNullDescriptors(statics: Map<JcField, UTestValueDescriptor?>): Map<JcField, UTestValueDescriptor> =
        statics.filterValues { it != null }.mapValues { it.value!! }

    private fun JcField.needToBuildDescriptor(): Boolean {
        if (isStatic && !isFinal) return true
        return isStatic && isFinal && enclosingClass.isEnum && enclosingClass.enumValues?.contains(this) == true
    }

    private fun buildDescriptor(jcField: JcField, descriptorBuilder: Value2DescriptorConverter): UTestValueDescriptor? {
        val jField = jcField.toJavaField(workerClassLoader) ?: return null
        val jFieldValue = jField.getFieldValue(null)
        val cp = jcField.enclosingClass.classpath
        val jFieldValueDescriptor = descriptorBuilder.buildDescriptorResultFromAny(jFieldValue, jcField.type.toJcType(cp))
        return jFieldValueDescriptor.getOrNull()
    }

    fun rollBackStatics() {
        for ((jcField, descrAfter) in stateAfterStaticsDescriptors) {
            if (descrAfter == null) continue
            val descrBefore = builtInitialDescriptors[jcField] ?: continue
            if (descrBefore.structurallyEqual(descrAfter)) continue
            val valueBeforeExec = descriptor2ValueConverter.buildObjectFromDescriptor(descrBefore)
            jcField.toJavaField(workerClassLoader)?.setFieldValue(null, valueBeforeExec)
        }
        stateAfterStaticsDescriptors.clear()
    }

}