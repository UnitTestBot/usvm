rootProject.name = "usvm"

include("usvm-core")
include("usvm-jvm")
include("usvm-ts")
include("usvm-util")
include("usvm-jvm-instrumentation")
include("usvm-sample-language")
include("usvm-dataflow")
include("usvm-jvm-dataflow")
include("usvm-ts-dataflow")

include("usvm-python")
include("usvm-python:cpythonadapter")
findProject(":usvm-python:cpythonadapter")?.name = "cpythonadapter"
include("usvm-python:usvm-python-annotations")
findProject(":usvm-python:usvm-python-annotations")?.name = "usvm-python-annotations"
include("usvm-python:usvm-python-main")
findProject(":usvm-python:usvm-python-main")?.name = "usvm-python-main"
include("usvm-python:usvm-python-runner")
findProject(":usvm-python:usvm-python-runner")?.name = "usvm-python-runner"
include("usvm-python:usvm-python-commons")
findProject(":usvm-python:usvm-python-commons")?.name = "usvm-python-commons"

// Actually, `includeBuild("../jacodb")` is enough, but there is a bug in IDEA when path is a symlink.
// As a workaround, we convert it to a real absolute path.
// See IDEA bug: https://youtrack.jetbrains.com/issue/IDEA-329756
// includeBuild(file("../jacodb").toPath().toRealPath().toAbsolutePath())

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}
