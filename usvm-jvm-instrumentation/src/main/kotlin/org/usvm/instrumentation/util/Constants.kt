package org.usvm.instrumentation.util

import org.usvm.instrumentation.rd.StaticsRollbackStrategy
import kotlin.time.Duration.Companion.seconds

//TODO move in common settings file
object InstrumentationModuleConstants {

    //Timeout for method execution
    val testExecutionTimeout = 10.seconds
    //Timeout for executor process waiting (should be in seconds)
    const val concreteExecutorProcessTimeout = 120
    //If something gone wrong with RD
    const val triesToRecreateExecutorRdProcess = 3
    //Rollback strategy
    val testExecutorStaticsRollbackStrategy = StaticsRollbackStrategy.REINIT
    //Max depth of descriptor construction
    val maxDepthOfDescriptorConstruction = 5

    const val nameForExistingButNullString = "USVM_GENERATED_NULL_STRING"

    //Passes as environment parameter
    val pathToUsvmInstrumentationJar: String
        get() = System.getenv("usvm-jvm-instrumentation-jar")

    val pathToUsvmCollectorsJar: String
        get() = System.getenv("usvm-jvm-collectors-jar")

    val pathToJava: String
        get() = System.getenv()["JAVA_HOME"] ?: System.getProperty("java.home")

}