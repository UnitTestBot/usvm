import usvmpython.MANUAL_RUN_GROUP_NAME
import usvmpython.MANUAL_TEST_FOR_RUNNER
import usvmpython.MANUAL_TEST_FOR_RUNNER_ENTRY
import usvmpython.USVM_PYTHON_COMMONS_MODULE

plugins {
    id("usvm.kotlin-conventions")
    `maven-publish`
}

dependencies {
    implementation(project(mapOf("path" to ":$USVM_PYTHON_COMMONS_MODULE")))

    testImplementation(Libs.logback)
}

tasks.register<JavaExec>(MANUAL_TEST_FOR_RUNNER) {
    group = MANUAL_RUN_GROUP_NAME
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs = listOf("-Dproject.root=${projectDir.parent}")
    mainClass.set(MANUAL_TEST_FOR_RUNNER_ENTRY)
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/UnitTestBot/usvm")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("jar") {
            from(components["java"])
            groupId = "org.usvm"
            artifactId = project.name
        }
    }
}
