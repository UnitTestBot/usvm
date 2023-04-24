plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("com.github.UnitTestBot.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("com.github.UnitTestBot.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("com.github.UnitTestBot.ksmt:ksmt-bitwuzla:${Versions.ksmt}")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
}