plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    api("io.github.microutils:kotlin-logging:${Versions.klogging}")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}

tasks.register<JavaExec>("manualTestOfRunner") {
    group = "run"
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs = listOf("-Dproject.root=${projectDir.parent}")
    mainClass.set("org.usvm.runner.ManualTestKt")
}