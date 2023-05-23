plugins {
    id("usvm.kotlin-conventions")
}

repositories {
    // jacodb
//    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    mavenLocal()
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")

    testImplementation("io.mockk:mockk:${Versions.mockk}")
}

sourceSets {
    val samples by creating {
        java {
            srcDir("src/samples/java")
        }
    }

    test {
        compileClasspath += samples.output
        runtimeClasspath += samples.output
    }
}
