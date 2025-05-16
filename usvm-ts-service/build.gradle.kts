import com.google.protobuf.gradle.id

plugins {
    id("usvm.kotlin-conventions")
    id(Plugins.GradleProtobuf)
}

dependencies {
    implementation(project(":usvm-ts"))

    implementation(Libs.jacodb_ets)
    implementation(Libs.grpc_protobuf)
    implementation(Libs.grpc_stub_kotlin)
    implementation(Libs.protobuf_kotlin)
    runtimeOnly(Libs.grpc_netty_shaded)
    implementation(Libs.grpc_services)

    if (JavaVersion.current().isJava9Compatible) {
        // Workaround for @javax.annotation.Generated
        // see: https://github.com/grpc/grpc-java/issues/3633
        implementation("javax.annotation:javax.annotation-api:1.3.2")
    }

    // TODO: remove logback dep
    implementation(Libs.logback)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)
}

protobuf {
    protoc {
        artifact = Libs.protobuf_protoc
    }
    plugins {
        id("grpc") {
            artifact = Libs.grpc_protoc_gen
        }
        id("grpckt") {
            artifact = Libs.grpc_protoc_gen_kotlin + ":jdk8@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without
                // options. Note the braces cannot be omitted, otherwise the
                // plugin will not be added. This is because of the implicit way
                // NamedDomainObjectContainer binds the methods.
                id("grpc") {}
                id("grpckt") {}
            }
            it.builtins {
                id("kotlin") {}
            }
        }
    }
}
