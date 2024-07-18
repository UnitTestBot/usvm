plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(Libs.kotlinx_collections)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
