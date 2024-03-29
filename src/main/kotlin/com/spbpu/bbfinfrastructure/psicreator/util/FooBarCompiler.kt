package com.spbpu.bbfinfrastructure.psicreator.util

import com.intellij.mock.MockProject
import com.intellij.openapi.util.Disposer
import com.intellij.pom.PomModel
import com.intellij.pom.PomTransaction
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.codeStyle.IndentHelper
import org.apache.commons.cli.CommandLine
import com.spbpu.bbfinfrastructure.psicreator.idea.MockCodeStyleManager
import com.spbpu.bbfinfrastructure.psicreator.idea.MockIndentHelper
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

/**
 * Created by akhin on 7/5/16.
 */

object FooBarCompiler {

    init {
//        Extensions.getRootArea().registerExtensionPoint(
//            TreeCopyHandler.EP_NAME.name,
//            TreeCopyHandler::class.java.canonicalName,
//            ExtensionPoint.Kind.INTERFACE
//        )
    }

    fun analyzeBunchOfSources(
        env: KotlinCoreEnvironment,
        files: Collection<KtFile>,
        cfg: CompilerConfiguration
    ): BindingContext? {
        return TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            env.project,
            files,
            CliBindingTrace(),
            cfg,
            { scope -> JvmPackagePartProvider(env.configuration.languageVersionSettings, scope) }
        ).bindingContext
    }

    fun setupMyCfg(cmd: CommandLine): CompilerConfiguration {

        val cfg = CompilerConfiguration()

        val jdkRoots = PathUtil.getJdkClassesRootsFromCurrentJre()
        val kotlinRoots = PathUtilEx.getKotlinPathsForCompiler()

        // TODO: Do not add the same jar file twice

        cfg.addJvmClasspathRoots(jdkRoots)
        cfg.addJvmClasspathRoots(kotlinRoots)
        cfg.addJvmClasspathRoots(cmd.jarFiles.map(::File))

        if (cmd.pomFile.isNotEmpty()) {
            setupFromPom(File(cmd.pomFile), cfg)
        }

        cfg.addKotlinSourceRoots(cmd.kotlinRoots)
        cfg.addKotlinSourceRoots(cmd.targetRoots)

        cfg.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        cfg.put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)

        return cfg
    }

    fun setupMyEnv(cfg: CompilerConfiguration): KotlinCoreEnvironment {

        val disposable = Disposer.newDisposable()
        val env = KotlinCoreEnvironment.createForProduction(
            disposable,
            cfg,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        val project = env.project as MockProject
        class MyPomModelImpl(env: KotlinCoreEnvironment) : PomModelImpl(env.project) {
            override fun runTransaction(pt: PomTransaction) = pt.run()
        }
        project.registerService(
            TreeAspect::class.java,
            TreeAspect()
        )

        val pomModel = MyPomModelImpl(env)


        project.registerService(
            PomModel::class.java,
            pomModel
        )

        project.registerService(
            CodeStyleManager::class.java,
            MockCodeStyleManager(env.project)
        )

        project.registerService(
            IndentHelper::class.java,
            MockIndentHelper()
        )

        return env
    }


    //    fun setupMyEnv(cfg: CompilerConfiguration): KotlinCoreEnvironment {
//
//        val disposable = Disposer.newDisposable()
//        //Use for windows
//        //System.setProperty("idea.io.use.fallback", "true")
//        val env = KotlinCoreEnvironment.createForProduction(
//            disposable,
//            cfg,
//            EnvironmentConfigFiles.JVM_CONFIG_FILES
//        )
//        val project = env.project as MockProject
//        project.registerService(
//            TreeAspect::class.java,
//            TreeAspect()
//        )
//
//        class MyPomModelImpl(env: KotlinCoreEnvironment) : PomModelImpl(env.project) {
//            override fun runTransaction(pt: PomTransaction) = pt.run()
//        }
//
//
//        val pomModel = MyPomModelImpl(env)
//
//        project.registerService(
//            PomModel::class.java,
//            pomModel
//        )
//        return env
//    }

    fun tearDownMyEnv(env: KotlinCoreEnvironment) = Disposer.dispose(env.project)

}
