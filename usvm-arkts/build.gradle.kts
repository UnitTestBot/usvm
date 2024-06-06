plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("com.github.UnitTestBot.jacodb:jacodb-analysis:${Versions.arktsJacoDBVersion}")
    implementation("com.github.UnitTestBot.jacodb:jacodb-core:${Versions.arktsJacoDBVersion}")
    implementation("com.github.UnitTestBot.jacodb:jacodb-panda-dynamic:${Versions.arktsJacoDBVersion}")
    implementation("com.github.UnitTestBot.jacodb:jacodb-panda-static:${Versions.arktsJacoDBVersion}")
    implementation("com.github.UnitTestBot.jacodb:jacodb-panda-analysis:${Versions.arktsJacoDBVersion}")

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

val `usvm-api` by sourceSets.creating {
    java {
        srcDir("src/usvm-api/java")
    }
}

// no module with name 'jacodb-api' in jacodb ??

//val `usvm-apiCompileOnly`: Configuration by configurations.getting
//dependencies {
//    `usvm-apiCompileOnly`("org.jacodb:jacodb-api:${Versions.arktsJacoDBVersion}")
//}
