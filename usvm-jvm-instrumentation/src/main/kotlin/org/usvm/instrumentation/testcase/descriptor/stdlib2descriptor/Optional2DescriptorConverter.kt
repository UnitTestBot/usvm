package org.usvm.instrumentation.testcase.descriptor.stdlib2descriptor

import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findDeclaredMethodOrNull
import org.jacodb.api.ext.objectType
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestConstantDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.util.toJcClassOrInterface
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class Optional2DescriptorConverter : StdlibValue2DescriptorConverter() {

    override fun convert(
        value: Any,
        jcClasspath: JcClasspath,
        parentConverter: Value2DescriptorConverter,
        originUTestInst: UTestInst?
    ): UTestAdvancedObjectDescriptor {
        val jClass = value::class.java
        val jcClass = jClass.toJcClassOrInterface(jcClasspath) ?: error("cant find ${jClass.name} in classpath")
        val methodToAdd = jcClass.findDeclaredMethodOrNull("of") ?: error("cant find method add in ${jcClass.name}")
        val valueAsOptional = value as Optional<*>
        val (instantiationChain, descriptor) = createDescriptor(jcClasspath, jcClass, originUTestInst, value)
        val componentType = jcClasspath.objectType
        return parentConverter.createCyclicRef(descriptor, value) {
            val descriptorForArrayListEl =
                parentConverter.buildDescriptorResultFromAny(valueAsOptional.getOrNull(), componentType).getOrNull()
                    ?: UTestConstantDescriptor.Null(componentType)
            instantiationChain.add(methodToAdd to listOf(descriptorForArrayListEl))
        }
    }

    override fun isPossibleToConvert(value: Any): Boolean = true
}