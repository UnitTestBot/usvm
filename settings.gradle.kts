rootProject.name = "usvm"

include("usvm-core")
include("usvm-jvm")
include("usvm-util")
include("usvm-instrumentation")

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}