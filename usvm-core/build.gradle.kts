plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    api(project(":usvm-util"))

    api("io.ksmt:ksmt-core:${Versions.ksmt}")
    api("io.ksmt:ksmt-z3:${Versions.ksmt}")
    api("io.github.microutils:kotlin-logging:${Versions.klogging}")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
}
