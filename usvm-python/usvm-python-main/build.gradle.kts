plugins {
    id("usvm.kotlin-conventions")
}

val headerPath = File(parent!!.childProjects["cpythonadapter"]!!.buildDir, "adapter_include")

tasks.compileJava {
    // to suppress "No processor claimed any of these annotations: org.jetbrains.annotations.Nullable,org.jetbrains.annotations.NotNull"
    options.compilerArgs.add("-Xlint:-processing")
    options.compilerArgs.add("-AheaderPath=${headerPath.canonicalPath}")
    outputs.dirs(headerPath)
}

// from GRADLE_USER_HOME/gradle.properties
val githubUserFromHome: String? by project
val githubTokenFromHome: String? by project  // with permission to read packages

val githubUser: String = githubUserFromHome ?: System.getenv("GITHUB_ACTOR") ?: error("githubUser not defined")
val githubToken: String = githubTokenFromHome ?: System.getenv("GITHUB_TOKEN") ?: error("githubToken not defined")

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/tochilinak/UTBotJava")
        credentials {
            username = githubUser
            password = githubToken
        }
    }
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(mapOf("path" to ":usvm-python:usvm-python-annotations")))
    annotationProcessor(project(":usvm-python:usvm-python-annotations"))

    implementation("org.utbot:utbot-python-types:2023.11-SNAPSHOT")

    // implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    // implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    // implementation("io.ksmt:ksmt-bitwuzla:${Versions.ksmt}")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:${Versions.collections}")

    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}