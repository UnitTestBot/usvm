plugins {
    id("org.gradle.application")
    id("usvm.kotlin-conventions")
}

tasks {
    test {
        jvmArgs(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+EnableJVMCI",
            "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.code=ALL-UNNAMED",
            "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.code.site=ALL-UNNAMED",
            "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.hotspot=ALL-UNNAMED",
            "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.meta=ALL-UNNAMED",
            "--add-exports", "jdk.internal.vm.ci/jdk.vm.ci.runtime=ALL-UNNAMED",
            "--add-opens", "java.base/java.nio=ALL-UNNAMED"
        )
    }
}

dependencies {
    implementation(project(":usvm-core"))

    implementation(files("libs/nalim.jar"))

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation(group = "org.slf4j", name = "slf4j-simple", version = Versions.slf4j)
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}