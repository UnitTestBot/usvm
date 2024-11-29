import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(Libs.kotlinx_collections)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        allWarningsAsErrors = false
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
