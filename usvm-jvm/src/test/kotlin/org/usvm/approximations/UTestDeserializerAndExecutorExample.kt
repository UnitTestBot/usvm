package org.usvm.approximations

import org.junit.jupiter.api.Test
import org.usvm.samples.JacoDBContainer
import org.usvm.samples.JavaMethodTestRunner.Companion.samplesClasspath
import org.usvm.samples.samplesKey
import org.usvm.util.JcTestExecutor
import java.io.File

class UTestDeserializerAndExecutorExample {

    private val jacodbCpKey: String
        get() = samplesKey

    private val classpath: List<File>
        get() = samplesClasspath

    protected val cp by lazy {
        JacoDBContainer(jacodbCpKey, classpath).cp
    }

    //For additional execution info look deeper in JcTestExecutor
    private val jcTestExecutor = JcTestExecutor(classpath = cp)

    @Test
    fun deserializeAndExecute() {
        val pathToTestsDirectory = "src/test/resources/serializedApproximationsTests/"
        val dirWithIntegerTests = File("$pathToTestsDirectory/java.lang.Integer")
       dirWithIntegerTests.listFiles()?.forEach { serializedTest ->
           println("DESERIALIZING AND EXECUTING TEST ${serializedTest.name}")
           val deserializedUTest = UTestSerializer().deserialize(serializedTest.readBytes(), cp)
           val executionResult = jcTestExecutor.executeUTest(deserializedUTest)
           println("EXECUTION RESULT: $executionResult")
       }
        jcTestExecutor.terminateExecutor()
    }


}