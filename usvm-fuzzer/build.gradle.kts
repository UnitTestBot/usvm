plugins {
    id("usvm.kotlin-conventions")
    id("com.jetbrains.rdgen") version Versions.rd
    application
    java
}

dependencies {
    implementation(project(":usvm-jvm-instrumentation"))
    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")
}

//tasks.withType<JavaExec> {
//    environment(
//        "usvm-jvm-instrumentation-jar",
//        buildDir.resolve("libs").resolve("usvm-jvm-instrumentation-1.0.jar").absolutePath
//    )
//    environment(
//        "usvm-jvm-collectors-jar",
//        buildDir.resolve("libs").resolve("usvm-jvm-instrumentation-collectors.jar").absolutePath
//    )
//}