plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(Libs.jacodb_ets)

    testImplementation(Libs.logback)
}
