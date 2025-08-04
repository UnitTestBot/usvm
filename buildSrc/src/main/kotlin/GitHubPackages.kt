import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

/**
 * Set up GitHub Packages Registry for a specific repository.
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
fun RepositoryHandler.githubPackages(
    project: Project,
    repo: String,
    setup: MavenArtifactRepository.() -> Unit = {},
) = with(project) {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/$repo")
        credentials {
            username = findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
        setup()
    }
}
