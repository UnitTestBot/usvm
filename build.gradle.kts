plugins {
    id("usvm.kotlin-conventions")
}

tasks.register("validateProjectList") {
    group = "verification"
    description = "Checks that the list of subprojects is exactly the expected."

    doLast {
        // Define the expected subprojects here.
        val expectedProjects = setOf(
            project(":usvm-core"),
            project(":usvm-util"),
            project(":usvm-dataflow"),
            project(":usvm-sample-language"),
            project(":usvm-jvm"),
            project(":usvm-jvm-dataflow"),
            project(":usvm-jvm-instrumentation"),
            project(":usvm-python"),
            project(":usvm-ts"),
            project(":usvm-ts-dataflow"),
        )

        // Gather the actual subprojects from the current root project.
        // Note: 'project.subprojects' is recursive!
        val actualProjects = project.subprojects - project(":usvm-python").subprojects

        // Compare and throw an error if something is missing or unexpected.
        val missingProjects = expectedProjects - actualProjects
        if (missingProjects.isNotEmpty()) {
            throw GradleException("Missing subprojects (${missingProjects.size}): $missingProjects")
        }
        val unexpectedProjects = actualProjects - expectedProjects
        if (unexpectedProjects.isNotEmpty()) {
            throw GradleException("Unexpected subprojects (${unexpectedProjects.size}): $unexpectedProjects")
        }
    }
}
