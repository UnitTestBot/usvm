plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation("com.squareup.moshi:moshi:1.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.11.0")
    implementation("com.squareup.moshi:moshi-adapters:1.11.0")
}

tasks.test {
    onlyIf {
        project.hasProperty("utbot-python-types")
    }
}