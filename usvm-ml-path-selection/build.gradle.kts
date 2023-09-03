plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version "1.8.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":usvm-core"))
//    testImplementation(project(":usvm-core"))

    implementation(project(":usvm-jvm"))
//    testImplementation(project(":usvm-jvm"))

    implementation(project(":usvm-util"))
//    testImplementation(project(":usvm-util"))

    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-symfpu:${Versions.ksmt}")

//    testImplementation("io.mockk:mockk:${Versions.mockk}")
//    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
//    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("io.mockk:mockk:${Versions.mockk}")
    implementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")

    // https://mvnrepository.com/artifact/org.burningwave/core
    // Use it to export all modules to all
//    testImplementation("org.burningwave:core:12.62.7")
    implementation("org.burningwave:core:12.62.7")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    api("io.ksmt:ksmt-core:${Versions.ksmt}")
    api("io.ksmt:ksmt-z3:${Versions.ksmt}")
    api("io.github.microutils:kotlin-logging:${Versions.klogging}")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
//    testImplementation("io.mockk:mockk:${Versions.mockk}")
//    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
    implementation("io.mockk:mockk:${Versions.mockk}")
    implementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")

//    testImplementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")

    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", Versions.serialization)
    implementation("io.github.rchowell", "dotlin", Versions.graphViz)

    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-symfpu:${Versions.ksmt}")

//    testImplementation("io.mockk:mockk:${Versions.mockk}")
//    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
//    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("io.mockk:mockk:${Versions.mockk}")
    implementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
}
