package org.usvm.instrumentation.util

import org.usvm.instrumentation.rd.StaticsRollbackStrategy
import kotlin.time.Duration.Companion.seconds

//TODO move in common settings file
object InstrumentationModuleConstants {

    //Timeout for method execution
    val timeout = 10.seconds
    //If something gone wrong with RD
    const val triesToRecreateRdProcess = 3
    //Rollback strategy
    val rollbackStrategy = StaticsRollbackStrategy.REINIT


    //Passes as environment parameter
    val pathToUsvmInstrumentationJar: String
        get() = System.getenv("usvm-instrumentation-jar")

    val pathToJava: String
        get() = System.getenv()["JAVA_HOME"] ?: System.getProperty("java.home")

}