plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
    application
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-ts"))
    implementation(project(":usvm-ts-pbt"))
    implementation(Libs.jacodb_ets)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(Libs.logback)
}

val fastCheckAdapterDir = project(":usvm-ts-pbt").layout.projectDirectory.dir("fast-check-adapter")
val fastCheckRuntimeProperty = "org.usvm.ts.pbt.fastcheck.runtime"
val generatedBuildMetadataDirectory = layout.buildDirectory.dir("generated/resources/callsBuildMetadata")
val toolRevision = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map(String::trim)
val toolStatus = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "status", "--porcelain", "--untracked-files=all")
}.standardOutput.asText.map(String::trim)

val generateBuildMetadata = tasks.register("generateBuildMetadata") {
    inputs.property("toolRevision", toolRevision)
    inputs.property("toolStatus", toolStatus)
    outputs.dir(generatedBuildMetadataDirectory)

    doLast {
        val revision = toolRevision.get()
        val buildIdentity = if (toolStatus.get().isBlank()) revision else "$revision-dirty"
        val metadataFile = generatedBuildMetadataDirectory.get()
            .file("org/usvm/ts/calls/build.properties")
            .asFile
        metadataFile.parentFile.mkdirs()
        metadataFile.writeText("tool.revision=$buildIdentity\n", Charsets.UTF_8)
    }
}

sourceSets.main {
    resources.srcDir(generatedBuildMetadataDirectory)
}

tasks.processResources {
    dependsOn(generateBuildMetadata)
}

tasks.test {
    dependsOn(":usvm-ts-pbt:buildFastCheckAdapter")
    systemProperty(fastCheckRuntimeProperty, fastCheckAdapterDir.asFile.absolutePath)
}

application {
    mainClass = "org.usvm.ts.calls.CallsExperimentCliKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    dependsOn(":usvm-ts-pbt:buildFastCheckAdapter")
    systemProperty(fastCheckRuntimeProperty, fastCheckAdapterDir.asFile.absolutePath)
}

distributions {
    main {
        contents {
            into("lib/fast-check-adapter") {
                from(fastCheckAdapterDir)
                include("dist/src/**")
                include("node_modules/**")
                include("package.json")
            }
        }
    }
}

listOf("startScripts", "installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(":usvm-ts-pbt:buildFastCheckAdapter")
    }
}
