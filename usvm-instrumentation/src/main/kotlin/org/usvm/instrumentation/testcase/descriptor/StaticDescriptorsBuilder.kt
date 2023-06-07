package org.usvm.instrumentation.testcase.descriptor

import getFieldValue
import org.jacodb.api.JcField
import org.usvm.instrumentation.classloader.HierarchicalWorkerClassLoader
import org.usvm.instrumentation.classloader.UserClassLoaderWithPrimitiveStatics
import org.usvm.instrumentation.jacodb.util.toJavaClass
import org.usvm.instrumentation.jacodb.util.toJavaField
import setFieldValue

class StaticDescriptorsBuilder(
    private val workerClassLoader: HierarchicalWorkerClassLoader
) : Value2DescriptorConverter(workerClassLoader.getClassLoader(), null) {

    val builtDescriptors = HashMap<JcField, UTestValueDescriptor?>()
    private val stateAfterStaticsDescriptors = HashMap<JcField, UTestValueDescriptor?>()
    private val descriptor2ValueConverter = Descriptor2ValueConverter(workerClassLoader.getClassLoader())

    fun buildDescriptorsForLoadedStatics(): Result<Map<JcField, UTestValueDescriptor>> =
        try {
            val allStatics =
                workerClassLoader.loadedClassesWithStatics.flatMap { it.declaredFields.filter { it.isStatic } }
            val staticToValue = allStatics.associateWith { jcField ->
                builtDescriptors.getOrPut(jcField) { buildDescriptor(jcField) }
            }
            Result.success(filterNullDescriptors(staticToValue))
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun buildDescriptorsForStatics(fields: List<JcField>): Result<Map<JcField, UTestValueDescriptor>> =
        try {
            val descriptorMap = fields.associateWith { buildDescriptor(it) }
            stateAfterStaticsDescriptors.putAll(descriptorMap)
            Result.success(filterNullDescriptors(descriptorMap))
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun rollBackStatics() {
        for ((jcField, descrAfter) in stateAfterStaticsDescriptors) {
            if (jcField.enclosingClass.toJavaClass(workerClassLoader.getClassLoader()).classLoader !is UserClassLoaderWithPrimitiveStatics) {
                continue
            }
            if (descrAfter == null) continue
            val descrBefore = builtDescriptors[jcField] ?: continue
            if (descrBefore.structurallyEqual(descrAfter)) continue
            val valueBeforeExec = descriptor2ValueConverter.buildObjectFromDescriptor(descrBefore)
            jcField.toJavaField(workerClassLoader.getClassLoader()).setFieldValue(null, valueBeforeExec)
        }
        stateAfterStaticsDescriptors.clear()
    }

    private fun filterNullDescriptors(statics: Map<JcField, UTestValueDescriptor?>): Map<JcField, UTestValueDescriptor> =
        statics.filterValues { it != null }.mapValues { it.value!! }

    private fun buildDescriptor(jcField: JcField): UTestValueDescriptor? {
        val jField = jcField.toJavaField(workerClassLoader.getClassLoader())
        val jFieldValue = jField.getFieldValue(null)
        val jFieldValueDescriptor = buildDescriptorResultFromAny(jFieldValue)
        return jFieldValueDescriptor.getOrNull()
    }


}