package org.usvm.instrumentation.util

import org.usvm.instrumentation.rd.StaticsRollbackStrategy
import kotlin.time.Duration.Companion.seconds

//TODO move in common settings file
object InstrumentationModuleConstants {

    //Timeout for test execution
    val testExecutionTimeout = 10.seconds
    //Timeout for method execution
    val methodExecutionTimeout = 2.seconds
    //Timeout for executor process waiting (should be in seconds)
    const val concreteExecutorProcessTimeout = 120
    //If something gone wrong with RD
    const val triesToRecreateExecutorRdProcess = 3
    //Rollback strategy
    val testExecutorStaticsRollbackStrategy = StaticsRollbackStrategy.REINIT
    //Max depth of descriptor construction
    val maxDepthOfDescriptorConstruction = 5
    //Number of stacktrace elements for exception construction
    val maxStackTraceElements = 10

    const val nameForExistingButNullString = "USVM_GENERATED_NULL_STRING"

    //Environment variable used to pass path to collectors jar from main process to instrumented process
    val envVarForPathToUsvmCollectorsJarPath = "usvm-jvm-collectors-jar"
}