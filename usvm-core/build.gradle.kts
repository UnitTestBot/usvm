plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    api(project(":usvm-util"))

    api("com.github.UnitTestBot.ksmt:ksmt-core:${Versions.ksmt}")
    api("com.github.UnitTestBot.ksmt:ksmt-z3:${Versions.ksmt}")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
}