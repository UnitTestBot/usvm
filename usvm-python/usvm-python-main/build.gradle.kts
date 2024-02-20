plugins {
    id("usvm.kotlin-conventions")
}

val headerPath = File(parent!!.childProjects["cpythonadapter"]!!.buildDir, "adapter_include")

tasks.compileJava {
    // to suppress "No processor claimed any of these annotations: org.jetbrains.annotations.Nullable,org.jetbrains.annotations.NotNull"
    options.compilerArgs.add("-Xlint:-processing")
    options.compilerArgs.add("-AheaderPath=${headerPath.canonicalPath}")
    outputs.dirs(headerPath)
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(mapOf("path" to ":usvm-python:usvm-python-annotations")))
    implementation(project(mapOf("path" to ":usvm-python:usvm-python-object-model")))
    annotationProcessor(project(":usvm-python:usvm-python-annotations"))

    implementation("com.github.UnitTestBot:PythonTypesAPI:${Versions.pythonTypesAPIHash}")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}