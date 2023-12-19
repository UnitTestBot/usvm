package org.usvm.instrumentation.executor

data class InstrumentationProcessPaths(
    val pathToUsvmInstrumentationJar: String = System.getenv("usvm-jvm-instrumentation-jar"),
    val pathToUsvmCollectorsJar: String = System.getenv("usvm-jvm-collectors-jar"),
    val pathToJava: String = System.getenv()["JAVA_HOME"] ?: System.getProperty("java.home")
)