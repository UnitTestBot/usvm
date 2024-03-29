package com.spbpu.bbfinfrastructure.psicreator

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.psicreator.util.FooBarCompiler.setupMyCfg
import com.spbpu.bbfinfrastructure.psicreator.util.FooBarCompiler.setupMyEnv
import com.spbpu.bbfinfrastructure.psicreator.util.JvmResolveUtil
import com.spbpu.bbfinfrastructure.psicreator.util.opt
import com.spbpu.bbfinfrastructure.util.CompilerArgs
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File

typealias IntellijProject = com.intellij.openapi.project.Project

@Suppress("DEPRECATION")
object PSICreator {

    private var targetFiles: List<KtFile> = listOf()
    private lateinit var cfg: CompilerConfiguration
    private lateinit var env: KotlinCoreEnvironment
    var curProject: Project? = null

    fun getPsiForJava(text: String, proj: IntellijProject = Factory.file.project) =
        PsiFileFactory.getInstance(proj).createFileFromText(JavaLanguage.INSTANCE, text)

    fun getPSIForText(text: String, generateCtx: Boolean = true): KtFile {
        //Save to tmp
        val path = "tmp/tmp.kt"
        File(path).writeText(text)
        return getPSIForFile(path)
    }

    fun getPsiForTextWithName(text: String, fileName: String): KtFile {
        val path = "tmp/$fileName"
        File(path).writeText(text)
        return getPSIForFile(path)
    }

    fun getPSIForFile(path: String): KtFile {
        val newArgs = arrayOf("-t", path)

        val cmd = opt.parse(newArgs)

        cfg = setupMyCfg(cmd)
        env = setupMyEnv(cfg)

        if (!Extensions.getRootArea().hasExtensionPoint(TreeCopyHandler.EP_NAME.name)) {
            Extensions.getRootArea().registerExtensionPoint(
                TreeCopyHandler.EP_NAME.name,
                TreeCopyHandler::class.java.canonicalName,
                ExtensionPoint.Kind.INTERFACE
            )
        }

        targetFiles = env.getSourceFiles().map {
            val f = KtPsiFactory(it).createFile(it.virtualFile.path, it.text)
            f.originalFile = it
            f
        }

        return targetFiles.first()
    }

    fun analyze(psiFile: PsiFile): BindingContext? = analyze(psiFile, curProject)

    fun analyzeAndGetModuleDescriptor(psiFile: PsiFile) = getAnalysisResult(psiFile, curProject)?.moduleDescriptor

    fun analyze(psiFile: PsiFile, project: Project?): BindingContext? =
        getAnalysisResult(psiFile, project)?.bindingContext

    fun getAnalysisResult(
        psiFile: PsiFile,
        project: Project?
    ): AnalysisResult? {
        //if (psiFile !is KtFile) return null
        project?.saveOrRemoveToTmp(true)
        val cmd = opt.parse(arrayOf())
        val cfg = setupMyCfg(cmd)

        cfg.put(JVMConfigurationKeys.INCLUDE_RUNTIME, true)
        cfg.put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home")))
        cfg.addJvmClasspathRoots(
            listOf(
                CompilerArgs.pathToOwaspJar
            ).map { File(it) }
        )

        project?.files?.map { it.name }?.let { fileNames ->
            val kotlinSources = fileNames.filter { it.endsWith(".kt") }
            val javaSources = fileNames.filter { it.endsWith(".java") }
            cfg.addJavaSourceRoots(javaSources.map { File(it) })
            cfg.addKotlinSourceRoots(kotlinSources)
        }

        val env = setupMyEnv(cfg)
        val configuration = env.configuration.copy()
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "root")
        return try {
            if (psiFile is KtFile) {
                JvmResolveUtil.analyze(listOf(psiFile), env, configuration)
            } else {
                JvmResolveUtil.analyze(env)
            }
        } catch (e: Exception) {
            println(e)
            null
        } finally {
            project?.saveOrRemoveToTmp(false)
        }
    }


}