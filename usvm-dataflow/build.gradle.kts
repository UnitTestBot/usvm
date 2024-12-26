plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-util"))
    api(Libs.jacodb_api_common)
    implementation(Libs.jacodb_taint_configuration)
    api(Libs.sarif4k)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
