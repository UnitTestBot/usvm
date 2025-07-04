import com.squareup.wire.kotlin.grpcserver.GrpcServerSchemaHandler

plugins {
    id("usvm.kotlin-conventions")
    id(Plugins.Wire)
}

buildscript {
    dependencies {
        classpath(Libs.wire_grpc_server_generator)
    }
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(project(":usvm-ts-dataflow"))

    protoSource("org.jacodb:wire-protos") // TODO: fix to 'Libs.jacodb_ets_wire_protos'
    implementation(Libs.jacodb_ets)
    implementation(Libs.grpc_protobuf)
    implementation(Libs.grpc_stub)
}

wire {
    // This is the replacement for `protoSource` dependency.
    // It works, but breaks reflection in grpc_cli,
    // even with manually specified `--proto_path` option.
    // protoPath {
    //     srcDir("src/main/extraproto")
    // }
    custom {
        schemaHandlerFactory = GrpcServerSchemaHandler.Factory()
        options = mapOf(
            "rpcCallStyle" to "blocking",
            "singleMethodServices" to "false",
        )
        exclusive = false
    }
    kotlin {
        rpcRole = "server"
        rpcCallStyle = "blocking"
        singleMethodServices = false
    }
}
