import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.sourceSets
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("io.leangen.geantyref:geantyref:1.3.14")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = false
        }
    }
}

tasks {
    register<Jar>("testJar") {
        group = "jar"
        shouldRunAfter("compileTestKotlin")
        archiveClassifier.set("test")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val contents = sourceSets.getByName("test").output

        from(contents)
    }
}

tasks.withType<Test> {
    environment(
        "usvm-jvm-instrumentation-jar",
        "/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation-1.0.jar"
    )
    environment(
        "usvm-jvm-collectors-jar",
        "/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation-collectors.jar"
    )
}