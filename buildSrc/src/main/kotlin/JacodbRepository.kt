import org.gradle.api.Project
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

/**
 * Set up GitHub Packages Registry for `jacodb`.
 *
 * To use this GPR locally, you need to set the `gpr.user` and `gpr.key` properties
 * in your `~/.gradle/gradle.properties` file, for example:
 * ```
 * gpr.user=your-github-username
 * gpr.key=your-github-token
 * ```
 * Note: token must have "packages: read" permissions.
 *
 * On CI, provide `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables.
 */
fun Project.setupJacodbGitHubPackagesRepository() {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/UnitTestBot/jacodb")
            credentials {
                username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
