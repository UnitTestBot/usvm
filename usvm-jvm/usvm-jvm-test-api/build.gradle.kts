plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(Libs.jacodb_api_jvm)
    implementation(project(":usvm-jvm:usvm-jvm-api"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
