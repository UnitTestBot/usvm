package org.usvm.instrumentation.testcase.descriptor.stdlib2descriptor

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findDeclaredMethodOrNull
import org.jacodb.api.ext.objectType
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestConstantDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.util.toJcClassOrInterface
import org.usvm.instrumentation.util.toJcType

class List2DescriptorConverter : StdlibValue2DescriptorConverter() {

    override fun convert(
        value: Any,
        jcClasspath: JcClasspath,
        parentConverter: Value2DescriptorConverter,
        originUTestInst: UTestInst?
    ): UTestAdvancedObjectDescriptor {
        val jClass = value::class.java
        val jcClass = jClass.toJcClassOrInterface(jcClasspath) ?: error("cant find ${jClass.name} in classpath")
        val methodToAdd = jcClass.findDeclaredMethodOrNull("add") ?: error("cant find method add in ${jcClass.name}")
        val valueAsList = value as List<*>
        val (instantiationChain, descriptor) = createDescriptor(jcClasspath, jcClass, originUTestInst, value)
        if (value.size == 0) return descriptor
        val componentType = jcClasspath.objectType

        return parentConverter.createCyclicRef(descriptor, value) {
            for (el in valueAsList) {
                val descriptorForArrayListEl =
                    parentConverter.buildDescriptorResultFromAny(el, componentType).getOrNull()
                        ?: UTestConstantDescriptor.Null(componentType)
                instantiationChain.add(methodToAdd to listOf(descriptorForArrayListEl))
            }
        }
    }

    override fun isPossibleToConvert(value: Any): Boolean {
        val valueAsList = value as List<*>
        val actualSize = valueAsList.fold(0) { acc, _ -> acc + 1 }
        return valueAsList.size == actualSize
    }

}