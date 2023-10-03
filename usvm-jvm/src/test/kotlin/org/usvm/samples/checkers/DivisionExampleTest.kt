package org.usvm.samples.checkers

import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.junit.jupiter.api.Test
import org.usvm.api.checkers.JcCheckerRunner
import org.usvm.samples.JacoDBContainer
import org.usvm.samples.samplesKey
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

// NOTE: THIS FILE MUST NOT BE MERGED!

class DivisionExampleTest {
    private fun makeClasspath(): JcClasspath = JacoDBContainer(samplesKey).cp

    @Test
    fun analyzeConstant() {
        val cp = makeClasspath()
        val runner = JcCheckerRunner(cp)
        val checker = JcDiv42Checker(runner.api, cp)

        val method = DivisionExample::divideBy42Constant
        val declaringClassName = requireNotNull(method.declaringClass?.name)
        val jcClass = cp.findClass(declaringClassName).toType()
        val jcMethod = jcClass.declaredMethods.first { it.name == method.name }

        runner.runChecker(jcMethod.method, checker)
    }

    @Test
    fun analyzeTN() {
        val cp = makeClasspath()
        val checker = JcCheckerRunner(cp)
        val checkersVisitor = JcDiv42Checker(checker.api, cp)

        val method = DivisionExample::divideBySymbolic42TN
        val declaringClassName = requireNotNull(method.declaringClass?.name)
        val jcClass = cp.findClass(declaringClassName).toType()
        val jcMethod = jcClass.declaredMethods.first { it.name == method.name }

        checker.runChecker(jcMethod.method, checkersVisitor)
    }

    @Test
    fun analyzeSymbolic() {
        val cp = makeClasspath()
        val checker = JcCheckerRunner(cp)
        val checkersVisitor = JcDiv42Checker(checker.api, cp)

        val method = DivisionExample::divideBySymbolic42
        val declaringClassName = requireNotNull(method.declaringClass?.name)
        val jcClass = cp.findClass(declaringClassName).toType()
        val jcMethod = jcClass.declaredMethods.first { it.name == method.name }

        checker.runChecker(jcMethod.method, checkersVisitor)
    }

    private val KFunction<*>.declaringClass: Class<*>?
        get() = (javaMethod ?: javaConstructor)?.declaringClass
}
