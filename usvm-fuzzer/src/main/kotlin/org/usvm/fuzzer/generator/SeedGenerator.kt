package org.usvm.fuzzer.generator

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.seed.Seed
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestNullExpression
import org.usvm.instrumentation.testcase.api.UTestStaticMethodCall
import org.usvm.instrumentation.util.toJcType
import org.usvm.instrumentation.util.typename

class SeedGenerator(
    private val jcClasspath: JcClasspath,
) {

    val dataGenerator = DataGenerator(jcClasspath)

    fun generateForMethod(jcMethod: JcMethod): Seed {
        val instance =
            if (!jcMethod.isStatic) {
                val uTestInstanceScenario =
                    dataGenerator.generateRandomParameterValue(jcMethod.enclosingClass.typename, 0)
                val instance =
                    if (uTestInstanceScenario.first != null) {
                        uTestInstanceScenario.first
                    } else {
                        UTestNullExpression(jcMethod.enclosingClass.toType())
                    }!!
                Seed.Descriptor(instance, jcMethod.returnType.toJcType(jcClasspath)!!, uTestInstanceScenario.second)
            } else {
                null
            }
        val args = jcMethod.parameters.map {
            val uTestParamScenario = dataGenerator.generateRandomParameterValue(it.type, 0)
            val paramInstance =
                if (uTestParamScenario.first == null) {
                    UTestNullExpression(it.type.toJcType(jcClasspath)!!)
                } else {
                    uTestParamScenario.first!!
                }
            Seed.Descriptor(paramInstance, jcMethod.returnType.toJcType(jcClasspath)!!, uTestParamScenario.second)
        }
        return if (jcMethod.isStatic) {
            Seed(jcMethod, args, null)
        } else {
            Seed(jcMethod, listOf(instance!!) + args, null)
        }
    }
}