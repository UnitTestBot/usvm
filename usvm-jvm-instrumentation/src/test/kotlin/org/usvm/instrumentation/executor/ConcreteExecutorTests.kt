package org.usvm.instrumentation.executor

import com.jetbrains.rd.util.first
import org.jacodb.api.jvm.ext.findTypeOrNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.instrumentation.testcase.api.UTestExecutionExceptionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.testcase.descriptor.UTestConstantDescriptor
import org.usvm.instrumentation.util.UTestCreator
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcreteExecutorTests: UTestConcreteExecutorTest() {


    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath = listOf("build/libs/usvm-jvm-instrumentation-test.jar")
            init()
        }

        @AfterAll
        @JvmStatic
        fun close() {
            uTestConcreteExecutor.close()
        }
    }

    @Test
    fun `simple test`() = executeTest {
        val uTest = UTestCreator.C.lol(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
    }

    @Test
    fun `arithmetic operation`() = executeTest {
        val uTest = UTestCreator.A.arithmeticOperation(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        val result = res.result
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(239, result.value)
    }

    @Test
    fun `exception`() = executeTest {
        val uTest = UTestCreator.A.exception(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionExceptionResult>(res)
        assertTrue(res.cause.stackTrace.isNotEmpty())
        assertEquals(res.cause.type, jcClasspath.findTypeOrNull<IllegalArgumentException>())
    }

    @Test
    fun `java reflection test`() = executeTest {
        val uTest = UTestCreator.A.getNumberOfClassConstructors(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(2, result.value)
    }

    @Test
    fun `annotated method test`() = executeTest {
        val uTest = UTestCreator.AnnotationsEx.getSelfAnnotationCount(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(1, result.value)
    }

    @Test
    @Disabled
    fun `annotation default value test`() = executeTest {
        val uTest = UTestCreator.AnnotationsEx.getAnnotationDefaultValue(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.String>(result)
        assertEquals("MyAnnotation default value", result.value)
    }

    @Test
    fun `static fields test`() = executeTest {
        repeat(3) {
            val uTest = UTestCreator.A.isA(jcClasspath)
            val res = uTestConcreteExecutor.executeAsync(uTest)
            assertIs<UTestExecutionSuccessResult>(res)
            val staticDescriptor = res.resultState.statics.first().value
            assertIs<UTestConstantDescriptor.Int>(staticDescriptor)
            assertEquals(staticDescriptor.value, 778)
        }
    }

    @Test
    fun `static method test`() = executeTest {
        val uTest = UTestCreator.A.javaStdLibCall(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
    }

    @Test
    fun `static interface method call test`() = executeTest {
        val uTest = UTestCreator.StaticInterfaceMethodCall.callStaticInterfaceMethod(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
    }

    @Test
    fun `method with uTestCondition test`() = executeTest {
        val uTest = UTestCreator.A.indexOfWithIf(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
    }

    @Test
    fun `method with inner class usage test`() = executeTest {
        val uTest = UTestCreator.Arrays.checkAllSamePoints(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
    }

    @Test
    fun `singleton test`() = executeTest {
        val uTest = UTestCreator.Singleton.addToArray(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
    }

    @Test
    fun `nested class test`() = executeTest {
        val uTest = UTestCreator.NestedClass.getB(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
    }

    @Test
    fun `simple class mock test`() = executeTest {
        val uTest = UTestCreator.A.mock(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(239, result.value)
    }

    @Test
    fun `mock static method test`() = executeTest {
        val uTest = UTestCreator.A.mockStaticMethod(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(239, result.value)
    }

    @Test
    fun `simple abstract class mock test`() = executeTest {
        val uTest = UTestCreator.A.mockAbstractClass(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(240, result.value)
    }

    @Test
    fun `mock java random`() = executeTest {
        val uTest = UTestCreator.A.mockRandom(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(239, result.value)
    }

    @Test
    fun `simple abstract class partially mocked test`() = executeTest {
        val uTest = UTestCreator.A.mockAbstractClass1(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        //Expected behavior
        assertEquals(1, result.value)
    }

    @Test
    @Disabled("Better support for multiple mocks")
    fun `multiple mock`() = executeTest {
        val uTest = UTestCreator.A.mockMultiple(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(239, result.value)
    }

    @Test
    fun `simple interface mock test`() = executeTest {
        val uTest = UTestCreator.A.mockInterface(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(239, result.value)
    }

    @Test
    fun `simple interface with default method mock test`() = executeTest {
        val uTest = UTestCreator.A.mockInterfaceWithDefaultMock(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        assertNotNull(res.result)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.Int>(result)
        assertEquals(239, result.value)
    }

    @Test
    fun `method with bug`() = executeTest {
        val uTest = UTestCreator.A.methodWithBug(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionExceptionResult>(res)
        assertEquals(false, res.cause.raisedByUserCode)
    }

    @Test
    fun `dynamic timeout`() = executeTest {
        val uTest = UTestCreator.SleepingClass.sleepFor(jcClasspath, 1_000L)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
    }

    @Test
    fun `nested`() = executeTest {
        val uTest = UTestCreator.Ex1.nestedDescriptors(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
    }

    @Test
    fun `get parent static field`() = executeTest {
        val uTest = UTestCreator.ParentStaticFieldUser.getParentStaticField(jcClasspath)
        val res = uTestConcreteExecutor.executeAsync(uTest)
        assertIs<UTestExecutionSuccessResult>(res)
        val result = res.result
        assertNotNull(result)
        assertIs<UTestConstantDescriptor.String>(result)
        assertEquals("static field content", result.value)
        assertContains(res.resultState.statics.keys.map { it.name }, "STATIC_FIELD")
    }

}
