plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("${Versions.jacodbPackage}:jacodb-core:${Versions.jacodb}")
    implementation("${Versions.jacodbPackage}:jacodb-panda-dynamic:${Versions.jacodb}")

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-symfpu:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-runner:${Versions.ksmt}")

    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")

    // https://mvnrepository.com/artifact/org.burningwave/core
    // Use it to export all modules to all
    testImplementation("org.burningwave:core:12.62.7")
}
