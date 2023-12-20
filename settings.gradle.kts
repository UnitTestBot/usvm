rootProject.name = "usvm"

include("usvm-core")
include("usvm-jvm")
include("usvm-util")
include("usvm-jvm-instrumentation")
include("usvm-sample-language")
include("usvm-dataflow")
include("usvm-jvm-dataflow")
include("usvm-python")
include("usvm-python:cpythonadapter")
findProject(":usvm-python:cpythonadapter")?.name = "cpythonadapter"
include("usvm-python:usvm-python-annotations")
findProject(":usvm-python:usvm-python-annotations")?.name = "usvm-python-annotations"
include("usvm-python:usvm-python-main")
findProject(":usvm-python:usvm-python-main")?.name = "usvm-python-main"
include("usvm-python:usvm-python-runner")
findProject(":usvm-python:usvm-python-runner")?.name = "usvm-python-runner"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}
include("usvm-python:usvm-python-object-model")
findProject(":usvm-python:usvm-python-object-model")?.name = "usvm-python-object-model"
