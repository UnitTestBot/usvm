plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
// uncomment for experiments
//    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
//    implementation("io.ksmt:ksmt-bitwuzla:${Versions.ksmt}")

    implementation(group =  "org.slf4j", name = "slf4j-simple", version = Versions.slf4j)
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")
}