package com.spbpu.bbfinfrastructure.psicreator.util

import org.apache.maven.model.Dependency
import org.apache.maven.model.Model
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelProblemCollector
import org.apache.maven.model.building.ModelProblemCollectorRequest
import org.apache.maven.model.interpolation.StringSearchModelInterpolator
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

/**
 * Created by akhin on 7/15/16.
 */

internal fun newRepositorySystem(): RepositorySystem {
    return MavenRepositorySystemUtils.newServiceLocator()
            .addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
            .addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
            .addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
            .getService(RepositorySystem::class.java)
}

internal fun newSession(system: RepositorySystem): RepositorySystemSession {
    val session = MavenRepositorySystemUtils.newSession()
    val localRepo = LocalRepository(System.getProperty("user.home") + "/.m2/repository")
    session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)
    return session
}

internal fun localRepo(): RemoteRepository {
    return RemoteRepository.Builder(
            "local",
            "default",
            "file:" + System.getProperty("user.home") + "/.m2/repository"
    ).build()
}

internal val Dependency.asAether: org.eclipse.aether.graph.Dependency
    get() = org.eclipse.aether.graph.Dependency(
            org.eclipse.aether.artifact.DefaultArtifact(
                    this.groupId,
                    this.artifactId,
                    this.type,
                    this.version
            ),
            this.scope,
            this.isOptional
    )

fun getPomModel(pom: File): Model {
    val pomReq = DefaultModelBuildingRequest().setPomFile(pom)
    val b = DefaultModelBuilderFactory().newInstance()
    val model = StringSearchModelInterpolator().interpolateModel(
            b.build(pomReq).rawModel,
            pom.parentFile,
            pomReq,
            object : ModelProblemCollector {
                override fun add(req: ModelProblemCollectorRequest?) {
                    // do nothing
                }
            }
    )
    return model
}

fun setupFromPom(
        pom: File,
        cfg: CompilerConfiguration): CompilerConfiguration {

    val model = getPomModel(pom)

    // TODO: get Kotlin roots from Maven info

    val repoSystem = newRepositorySystem()
    val session = newSession(repoSystem)
    val localRepo = localRepo()

    val collectReq = CollectRequest(
            model.dependencies.map { it.asAether },
            null,
            listOf(localRepo)
    )
    val depReq = DependencyRequest(collectReq, null)

    val res = repoSystem.resolveDependencies(session, depReq)

    res.artifactResults
            .filter { it.isResolved }
            .map { it.artifact.file }
            .forEach { cfg.addJvmClasspathRoot(it) }

    return cfg
}
