plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
}