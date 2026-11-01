import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

fun Project.configureDetekt() {
    val usesStrictTsRules = path in STRICT_TS_DETEKT_PROJECT_PATHS

    val detektToolVersion = extensions.getByType<DetektExtension>().toolVersion
    dependencies {
        add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:$detektToolVersion")
        if (usesStrictTsRules) {
            add("detektPlugins", project(USVM_DETEKT_RULES_PROJECT_PATH))
        }
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
    val tsConfigFile = rootDir.resolve("detekt").resolve("ts-config.yml")
    val reportFile = rootProject.layout.buildDirectory.file("reports/detekt/detekt.sarif")
    val configFiles = if (usesStrictTsRules) listOf(configFile, tsConfigFile) else listOf(configFile)

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        ignoreFailures = !usesStrictTsRules
        parallel = true

        config.setFrom(configFiles)
    }

    tasks.withType<Detekt> {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
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
        jvmTarget = JavaVersion.VERSION_1_8.toString()
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

private val STRICT_TS_DETEKT_PROJECT_PATHS = setOf(":usvm-ts", ":usvm-ts-pbt")
private const val USVM_DETEKT_RULES_PROJECT_PATH = ":usvm-detekt-rules"
