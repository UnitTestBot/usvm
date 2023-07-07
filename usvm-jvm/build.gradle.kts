plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")

    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
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

val samplesImplementation: Configuration by configurations.getting

dependencies {
    samplesImplementation("org.projectlombok:lombok:${Versions.samplesLombok}")
    samplesImplementation("org.slf4j:slf4j-api:${Versions.samplesSl4j}")
    samplesImplementation("javax.validation:validation-api:${Versions.samplesJavaxValidation}")
    samplesImplementation("com.github.stephenc.findbugs:findbugs-annotations:${Versions.samplesFindBugs}")
    samplesImplementation("org.jetbrains:annotations:${Versions.samplesJetbrainsAnnotations}")
}

tasks {
    withType<Test> {
        jvmArgs = listOf(
            "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens", "java.base/java.util.concurrent.locks=ALL-UNNAMED",
            "--add-opens", "java.base/java.text=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.nio=ALL-UNNAMED",
            "--add-opens", "java.base/java.nio.file=ALL-UNNAMED",
            "--add-opens", "java.base/java.net=ALL-UNNAMED",
            "--add-opens", "java.base/sun.security.util=ALL-UNNAMED",
            "--add-opens", "java.base/sun.reflect.generics.repository=ALL-UNNAMED",
            "--add-opens", "java.base/sun.net.util=ALL-UNNAMED",
            "--add-opens", "java.base/sun.net.fs=ALL-UNNAMED",
            "--add-opens", "java.base/java.security=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.ref=ALL-UNNAMED",
            "--add-opens", "java.base/java.math=ALL-UNNAMED",
            "--add-opens", "java.base/java.util.stream=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/sun.security.provider=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.event=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.jimage=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.jimage.decompressor=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.jmod=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.jtrfs=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.logger=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.math=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.module=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.org.objectweb.asm.signature=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.org.objectweb.asm.tree.analysis=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.org.objectweb.asm.util=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.org.xml.sax=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.org.xml.sax.helpers=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.perf=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.platform=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.ref=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.util=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.util.jar=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.util.xml=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.util.xml.impl=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.vm=ALL-UNNAMED",
            "--add-opens", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
            "--add-opens", "java.management/javax.management=ALL-UNNAMED",
            "--add-opens", "jdk.charsets/sun.nio.cs.ext=ALL-UNNAMED",
            "--add-opens", "jdk.zipfs/jdk.nio.zipfs=ALL-UNNAMED",
            "--add-opens", "java.sql/java.sql=ALL-UNNAMED",
            "--add-opens", "java.base/sun.net.www.http=ALL-UNNAMED",
            "--add-opens", "java.base/sun.net.ext=ALL-UNNAMED",
            "--add-opens", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        )
    }
}
