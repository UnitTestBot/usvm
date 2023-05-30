package org.usvm.instrumentation.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

//TODO move in common settings file
object InstrumentationModuleConstants {

    val timeout = 10.seconds
    const val triesToRecreateRdProcess = 3
    const val pathToUsvmInstrumentationJar = "usvm-instrumentation/build/libs/usvm-instrumentation-1.0.jar"

}