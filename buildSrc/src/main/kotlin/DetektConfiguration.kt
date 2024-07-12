import gradle.kotlin.dsl.accessors._466a692754d3da37fc853e1c7ad8ae1e.detekt
import gradle.kotlin.dsl.accessors._466a692754d3da37fc853e1c7ad8ae1e.detektPlugins
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

fun Project.configureDetekt() {
    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${detekt.toolVersion}")
    }

    val includes = listOf(
        "**/*.kt",
        "**/*.kts"
    )

    val excludes = listOf(
        "**/resources/**",
        "**/build/**",
        "**/samples/**",
        "**/generated/**",
    )

    val resolveBaselineFile = { project: String, task: String ->
        val taskPostfix = task.substringAfter("detekt").substringAfter("Baseline")
        rootDir
            .resolve("detekt")
            .resolve("baselines")
            .resolve("${project}-${taskPostfix}.yml")
    }
    val configFile = rootDir.resolve("detekt").resolve("config.yml")
    val reportFile = rootProject.layout.buildDirectory.file("reports/detekt/detekt.sarif")

    detekt {
        buildUponDefaultConfig = true
        ignoreFailures = true
        parallel = true

        config.setFrom(configFile)
    }

    tasks.withType<Detekt> {
        setIncludes(includes)
        setExcludes(excludes)

        reports {
            md.required = false
            txt.required = false
            xml.required = false
        }

        val baselineFile = resolveBaselineFile(project.name, this@withType.name)
        if (baselineFile.exists()) {
            baseline = baselineFile
        }
    }

    tasks.withType<DetektCreateBaselineTask> {
        baseline = resolveBaselineFile(project.name, this@withType.name)
    }


    if (project == rootProject) {
        tasks.register("reportMerge", ReportMergeTask::class.java) {
            group = "verification"
            output.set(reportFile)
        }
    } else {
        val reportMerge = rootProject.tasks.withType<ReportMergeTask>().single()
        tasks.withType<Detekt> {
            finalizedBy(reportMerge)
        }
        reportMerge.input.from(tasks.withType<Detekt>().map { it.sarifReportFile })
    }

    tasks.named("check").configure {
        setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "detekt" })
    }
}
