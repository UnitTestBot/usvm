plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    api(project(":usvm-util"))
    api(Libs.ksmt_core)
    api(Libs.ksmt_z3)
    api(Libs.kotlinx_collections)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.ksmt_yices)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
