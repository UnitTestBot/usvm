@file:Suppress("MemberVisibilityCanBePrivate", "PublicApiImplicitType", "unused", "ConstPropertyName", "FunctionName")

import org.gradle.plugin.use.PluginDependenciesSpec

object Versions {
    const val clikt = "5.0.0"
    const val detekt = "1.23.7"
    const val ini4j = "0.5.4"
    const val jacodb = "3b6a17f000"
    const val juliet = "1.3.2"
    const val junit = "5.9.3"
    const val kotlin = "2.1.0"
    const val kotlin_logging = "3.0.5"
    const val kotlinx_collections = "0.3.8"
    const val kotlinx_coroutines = "1.10.0"
    const val kotlinx_serialization = "1.7.3"
    const val ksmt = "0.5.26"
    const val logback = "1.4.8"
    const val mockk = "1.13.4"
    const val rd = "2023.2.0"
    const val sarif4k = "0.5.0"
    const val shadow = "8.3.3"
    const val slf4j = "1.6.1"

    // versions for jvm samples
    object Samples {
        const val lombok = "1.18.20"
        const val slf4j = "1.7.36"
        const val javaxValidation = "2.0.0.Final"
        const val findBugs = "1.3.9-1"
        const val jetbrainsAnnotations = "16.0.2"
    }

    const val pythonTypesAPI = "139b81d"
    const val utbotMypyRunner = "0.2.17"
}

fun dep(group: String, name: String, version: String): String = "$group:$name:$version"

object Libs {
    // https://github.com/junit-team/junit5
    const val junit_bom = "org.junit:junit-bom:${Versions.junit}"
    const val junit_jupiter = "org.junit.jupiter:junit-jupiter"
    val junit_jupiter_api = dep(
        group = "org.junit.jupiter",
        name = "junit-jupiter-api",
        version = Versions.junit
    )
    val junit_jupiter_engine = dep(
        group = "org.junit.jupiter",
        name = "junit-jupiter-engine",
        version = Versions.junit
    )
    val junit_jupiter_params = dep(
        group = "org.junit.jupiter",
        name = "junit-jupiter-params",
        version = Versions.junit
    )

    // https://github.com/oshai/kotlin-logging
    val kotlin_logging = dep(
        group = "io.github.microutils",
        name = "kotlin-logging",
        version = Versions.kotlin_logging
    )

    // https://github.com/qos-ch/slf4j
    val slf4j_simple = dep(
        group = "org.slf4j",
        name = "slf4j-simple",
        version = Versions.slf4j
    )

    // https://github.com/qos-ch/logback
    val logback = dep(
        group = "ch.qos.logback",
        name = "logback-classic",
        version = Versions.logback
    )

    // https://github.com/UnitTestBot/ksmt
    val ksmt_core = dep(
        group = "io.ksmt",
        name = "ksmt-core",
        version = Versions.ksmt
    )
    val ksmt_runner = dep(
        group = "io.ksmt",
        name = "ksmt-runner",
        version = Versions.ksmt
    )
    val ksmt_z3 = dep(
        group = "io.ksmt",
        name = "ksmt-z3",
        version = Versions.ksmt
    )
    val ksmt_yices = dep(
        group = "io.ksmt",
        name = "ksmt-yices",
        version = Versions.ksmt
    )
    val ksmt_cvc5 = dep(
        group = "io.ksmt",
        name = "ksmt-cvc5",
        version = Versions.ksmt
    )
    val ksmt_bitwuzla = dep(
        group = "io.ksmt",
        name = "ksmt-bitwuzla",
        version = Versions.ksmt
    )
    val ksmt_symfpu = dep(
        group = "io.ksmt",
        name = "ksmt-symfpu",
        version = Versions.ksmt
    )

    // https://github.com/UnitTestBot/jacodb
    private const val jacodbPackage = "com.github.UnitTestBot.jacodb" // use "org.jacodb" with includeBuild
    val jacodb_core = dep(
        group = jacodbPackage,
        name = "jacodb-core",
        version = Versions.jacodb
    )
    val jacodb_api_common = dep(
        group = jacodbPackage,
        name = "jacodb-api-common",
        version = Versions.jacodb
    )
    val jacodb_api_jvm = dep(
        group = jacodbPackage,
        name = "jacodb-api-jvm",
        version = Versions.jacodb
    )
    val jacodb_api_storage = dep(
        group = jacodbPackage,
        name = "jacodb-api-storage",
        version = Versions.jacodb
    )
    val jacodb_storage = dep(
        group = jacodbPackage,
        name = "jacodb-storage",
        version = Versions.jacodb
    )
    val jacodb_approximations = dep(
        group = jacodbPackage,
        name = "jacodb-approximations",
        version = Versions.jacodb
    )
    val jacodb_taint_configuration = dep(
        group = jacodbPackage,
        name = "jacodb-taint-configuration",
        version = Versions.jacodb
    )
    val jacodb_ets = dep(
        group = jacodbPackage,
        name = "jacodb-ets",
        version = Versions.jacodb
    )

    // https://github.com/Kotlin/kotlinx.coroutines
    val kotlinx_coroutines_core = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-coroutines-core",
        version = Versions.kotlinx_coroutines
    )

    // https://github.com/Kotlin/kotlinx.collections.immutable
    val kotlinx_collections = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-collections-immutable-jvm",
        version = Versions.kotlinx_collections
    )

    // https://github.com/Kotlin/kotlinx.serialization
    val kotlinx_serialization_core = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-serialization-core",
        version = Versions.kotlinx_serialization
    )
    val kotlinx_serialization_json = dep(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-serialization-json",
        version = Versions.kotlinx_serialization
    )

    // https://github.com/mockk/mockk
    val mockk = dep(
        group = "io.mockk",
        name = "mockk",
        version = Versions.mockk
    )

    // https://github.com/UnitTestBot/juliet-java-test-suite
    val juliet_support = dep(
        group = "com.github.UnitTestBot.juliet-java-test-suite",
        name = "support",
        version = Versions.juliet
    )

    fun juliet_cwe(cweNum: Int) = dep(
        group = "com.github.UnitTestBot.juliet-java-test-suite",
        name = "cwe${cweNum}",
        version = Versions.juliet
    )

    // https://github.com/detekt/sarif4k
    val sarif4k = dep(
        group = "io.github.detekt.sarif4k",
        name = "sarif4k",
        version = Versions.sarif4k
    )

    // https://github.com/JetBrains/rd
    val rd_core = dep(
        group = "com.jetbrains.rd",
        name = "rd-core",
        version = Versions.rd
    )
    val rd_framework = dep(
        group = "com.jetbrains.rd",
        name = "rd-framework",
        version = Versions.rd
    )
    val rd_gen = dep(
        group = "com.jetbrains.rd",
        name = "rd-gen",
        version = Versions.rd
    )

    // https://github.com/facebookarchive/ini4j
    val ini4j = dep(
        group = "org.ini4j",
        name = "ini4j",
        version = Versions.ini4j
    )

    // https://github.com/UnitTestBot/PythonTypesAPI
    val python_types_api = dep(
        group = "com.github.UnitTestBot",
        name = "PythonTypesAPI",
        version = Versions.pythonTypesAPI
    )

    // https://github.com/ajalt/clikt
    val clikt = dep(
        group = "com.github.ajalt.clikt",
        name = "clikt",
        version = Versions.clikt
    )
}

object Plugins {

    abstract class ProjectPlugin(val id: String, val version: String)

    // https://github.com/detekt/detekt
    object Detekt : ProjectPlugin(
        id = "io.gitlab.arturbosch.detekt",
        version = Versions.detekt
    )

    // https://github.com/JetBrains/rd
    object RdGen : ProjectPlugin(
        id = "com.jetbrains.rdgen",
        version = Versions.rd
    )

    // https://github.com/GradleUp/shadow
    object Shadow : ProjectPlugin(
        id = "com.gradleup.shadow",
        version = Versions.shadow
    )
}

fun PluginDependenciesSpec.id(plugin: Plugins.ProjectPlugin) {
    id(plugin.id).version(plugin.version)
}
