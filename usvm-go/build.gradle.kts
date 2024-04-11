import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    withType<KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = false
        }
    }
}

dependencies {
    implementation(project(":usvm-core"))

    implementation(files("libs/nalim.jar"))
    implementation(files("libs/jacodb-api-core-1.4-SNAPSHOT.jar"))

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("org.slf4j:slf4j-simple:${Versions.slf4j}")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}