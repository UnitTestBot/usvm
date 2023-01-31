plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation("com.github.UnitTestBot.ksmt:ksmt-core:${Versions.ksmt}")
    implementation("com.github.UnitTestBot.ksmt:ksmt-z3:${Versions.ksmt}")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
}