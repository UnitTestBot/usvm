rootProject.name = "usvm"

include("usvm-core")
include("usvm-jvm")
include("usvm-util")
include("usvm-jvm-instrumentation")
include("usvm-sample-language")
include("usvm-dataflow")
include("usvm-jvm-dataflow")

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}