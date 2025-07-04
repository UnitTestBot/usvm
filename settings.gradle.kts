rootProject.name = "usvm"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}

// Actually, `includeBuild("../jacodb")` is enough, but there is a bug in IDEA when path is a symlink.
// As a workaround, we convert it to a real absolute path.
// See IDEA bug: https://youtrack.jetbrains.com/issue/IDEA-329756
val jacodbPath = file("jacodb").takeIf { it.exists() }
    ?: file("../jacodb").takeIf { it.exists() }
    ?: error("Local JacoDB directory not found")
includeBuild(jacodbPath.toPath().toRealPath().toAbsolutePath())

val usvmPython = "usvm-python"
fun includePy(name: String) {
    include("$usvmPython:$name")
    project(":$usvmPython:$name").name = name
}
include(usvmPython)
includePy("cpythonadapter")
includePy("usvm-python-annotations")
includePy("usvm-python-main")
includePy("usvm-python-runner")
includePy("usvm-python-commons")

include("usvm-core")
include("usvm-util")
include("usvm-dataflow")
include("usvm-sample-language")

include("usvm-jvm")
include("usvm-jvm:usvm-jvm-api")
include("usvm-jvm:usvm-jvm-test-api")
include("usvm-jvm:usvm-jvm-util")
include("usvm-jvm-instrumentation")
include("usvm-jvm-dataflow")

include("usvm-ts")
include("usvm-ts-service")
include("usvm-ts-dataflow")
