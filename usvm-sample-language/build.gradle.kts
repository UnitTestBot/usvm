plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))

    implementation(Libs.ksmt_yices)
    // uncomment for experiments
    // implementation(Libs.ksmt_cvc5)
    // implementation(Libs.ksmt_bitwuzla)

    testImplementation(Libs.logback)
}
