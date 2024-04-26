package org.usvm.samples.checkers

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcDivExpr
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.toType
import org.junit.jupiter.api.Test
import org.usvm.api.checkers.JcCheckerRunner
import org.usvm.samples.JacoDBContainer
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.samplesKey
import org.usvm.util.declaringClass
import kotlin.reflect.KFunction
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertIsNot

class DivisionExampleTest {
    private val cp: JcClasspath = JacoDBContainer(samplesKey, JavaMethodTestRunner.samplesClasspath).cp

    @Test
    fun analyzeConstantSinglePath() {
        val jcMethod = DivisionExample::divideBy42ConstantSinglePath.jcMethod
        val targetStatements = runChecker(jcMethod)

        assertEquals(1, targetStatements.size)

        val value = targetStatements.single().rhv
        assertIs<JcDivExpr>(value)
    }

    @Test
    fun analyzeConstantBranching() {
        val jcMethod = DivisionExample::divideBy42ConstantBranching.jcMethod
        val targetStatements = runChecker(jcMethod)

        assertEquals(2, targetStatements.size)

        targetStatements.forEach { assertIs<JcDivExpr>(it.rhv) }
    }

    @Test
    fun analyzeConstantUnreachableBranch() {
        val jcMethod = DivisionExample::divideBy42ConstantWithUnreachableBranch.jcMethod
        val targetStatements = runChecker(jcMethod)

        assertEquals(1, targetStatements.size)

        val value = targetStatements.single().rhv
        assertIs<JcDivExpr>(value)

        val leftPartOfDivision = value.lhv
        assertIsNot<JcParameter>(leftPartOfDivision)
    }

    @Test
    fun analyzeSymbolicSinglePath() {
        val jcMethod = DivisionExample::divideBySymbolic42SinglePath.jcMethod
        val targetStatements = runChecker(jcMethod)

        assertEquals(1, targetStatements.size)

        val value = targetStatements.single().rhv
        assertIs<JcDivExpr>(value)
    }

    @Test
    fun analyzeSymbolicTrueNegative() {
        val jcMethod = DivisionExample::divideBySymbolic42TrueNegative.jcMethod
        val targetStatements = runChecker(jcMethod)

        assertEquals(0, targetStatements.size)
    }

    @Test
    fun analyzeSymbolicBranching() {
        val jcMethod = DivisionExample::divideBySymbolic42Branching.jcMethod
        val targetStatements = runChecker(jcMethod)

        assertEquals(2, targetStatements.size)

        targetStatements.forEach { assertIs<JcDivExpr>(it.rhv) }
    }

    @Test
    fun analyzeSymbolicFalsePositive() {
        val jcMethod = DivisionExample::divideBySymbolic42FalsePositive.jcMethod
        val targetStatements = runChecker(jcMethod)

        assertEquals(0, targetStatements.size)
    }

    private val KFunction<*>.jcMethod: JcTypedMethod
        get() {
            val declaringClassName = requireNotNull(declaringClass?.name)
            val jcClass = cp.findClass(declaringClassName).toType()

            return jcClass.declaredMethods.first { it.name == name }
        }

    private fun runChecker(jcMethod: JcTypedMethod): Set<JcAssignInst> {
        val checker = JcCheckerRunner(cp)
        val checkersVisitor = JcDiv42Checker(checker.api, cp)

        checker.runChecker(jcMethod.method, checkersVisitor)

        return checkersVisitor.targetStatements
    }
}
