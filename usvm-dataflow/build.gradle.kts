plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    api(project(":usvm-util"))
    api(Libs.jacodb_api_common)
    api(Libs.jacodb_taint_configuration)
    api(Libs.sarif4k)
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
