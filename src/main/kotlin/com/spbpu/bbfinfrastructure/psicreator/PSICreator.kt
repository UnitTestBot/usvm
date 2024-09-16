package com.spbpu.bbfinfrastructure.psicreator

import com.goide.GoElementTypeFactorySupplierImpl
import com.goide.GoFileType
import com.goide.GoLanguage
import com.goide.psi.impl.GoCaching
import com.intellij.core.*
import com.intellij.go.backend.GoBackendParserDefinition
import com.intellij.go.frontback.api.GoElementTypeFactorySupplier
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.EmptyModuleManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.PomModel
import com.intellij.pom.PomTransaction
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexExtension
import com.intellij.psi.stubs.StubIndexImpl
import com.jetbrains.python.*
import com.jetbrains.python.documentation.doctest.PyDocstringTokenSetContributor
import com.jetbrains.python.psi.PyAstElementGenerator
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.impl.PyElementGeneratorImpl
import com.jetbrains.python.psi.impl.PyPsiFacadeImpl
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.util.FooBarCompiler.setupMyCfg
import com.spbpu.bbfinfrastructure.psicreator.util.FooBarCompiler.setupMyEnv
import com.spbpu.bbfinfrastructure.psicreator.util.JvmResolveUtil
import com.spbpu.bbfinfrastructure.psicreator.util.opt
import com.spbpu.bbfinfrastructure.util.FuzzingConf
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File

typealias IntellijProject = com.intellij.openapi.project.Project

@Suppress("DEPRECATION")
object PSICreator {

    private lateinit var env: CoreProjectEnvironment
    var curProject: Project? = null

//    fun getPsiForJava(text: String, proj: IntellijProject = Factory.file.project) =
//        PsiFileFactory.getInstance(proj).createFileFromText(JavaLanguage.INSTANCE, text)

    fun getPsiForJava(text: String): PsiFile {
        if (!::env.isInitialized) {
            setEnvForJava()
        }
        val project = env.project
        return PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, text)
    }

    private fun setEnvForJava() {
        val disposable = Disposer.newDisposable()
        System.setProperty("idea.home.path", "lib/bin")
        val coreApplicationEnvironment = JavaCoreApplicationEnvironment(disposable, false)
        env = JavaCoreProjectEnvironment(disposable, coreApplicationEnvironment)
        env.project.registerService(TreeAspect::class.java, TreeAspect())
        class MyPomModelImpl(env: JavaCoreProjectEnvironment) : PomModelImpl(env.project) {
            override fun runTransaction(pt: PomTransaction) = pt.run()
        }
        val pomModel = MyPomModelImpl(env as JavaCoreProjectEnvironment)
        env.project.registerService(PomModel::class.java, pomModel)
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(PsiAugmentProvider.EP_NAME.name, PsiAugmentProvider::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(TreeCopyHandler.EP_NAME.name, TreeCopyHandler::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
    }

    fun getPsiForPython(text: String): PsiFile? {
        if (!::env.isInitialized) {
            setEnvForPython()
        }
        val project = env.project
        return PsiFileFactory.getInstance(project).createFileFromText(PythonLanguage.INSTANCE, text)
    }

    fun getPsiForGo(text: String): PsiFile? {
        if (!::env.isInitialized) {
            setEnvForGo()
        }
        val project = env.project
        return PsiFileFactory.getInstance(project).createFileFromText(GoLanguage.INSTANCE, text)
    }

    @Suppress("INACCESSIBLE_TYPE")
    private fun setEnvForGo() {
        val disposable = Disposable {  }
        System.setProperty("idea.home.path", "lib/bin")
        val coreApplicationEnvironment = CoreApplicationEnvironment(disposable, false)
        env = CoreProjectEnvironment(disposable, coreApplicationEnvironment)
        (FileTypeRegistry.getInstance() as CoreFileTypeRegistry).registerFileType(GoFileType.INSTANCE, "go")
        coreApplicationEnvironment.registerParserDefinition(GoLanguage.INSTANCE, GoBackendParserDefinition())
        val registryKeyDescriptorClass = Class.forName("com.intellij.openapi.util.registry.RegistryKeyDescriptor")
        val rkd = registryKeyDescriptorClass.declaredConstructors.first().let {
            it.isAccessible = true
            it.newInstance("go.light.ast.stubs.enabled", "true", "When enabled, uses the Light AST API for creating stubs (restart is required after changing the flag)", false, false, null)
        }
        val m = mutableMapOf("go.light.ast.stubs.enabled" to rkd)
        Registry::class.java.declaredMethods.find { it.name == "setKeys" }!!.invoke(null, m)
        coreApplicationEnvironment.registerApplicationService(GoElementTypeFactorySupplier::class.java, GoElementTypeFactorySupplierImpl())
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(
            SmartPointerAnchorProvider.EP_NAME.name,
            SmartPointerAnchorProvider::class.java.name,
            ExtensionPoint.Kind.INTERFACE,
            true
        )
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(
            StubIndexExtension.EP_NAME.name,
            StubIndexExtension::class.java.name,
            ExtensionPoint.Kind.INTERFACE,
            true
        )
        coreApplicationEnvironment.registerApplicationService(StubIndex::class.java, StubIndexImpl())
        GoCaching.setEnabled(false)
        env.project.registerService(TreeAspect::class.java, TreeAspect())
        class MyPomModelImpl(env: CoreProjectEnvironment) : PomModelImpl(env.project) {
            override fun runTransaction(pt: PomTransaction) = pt.run()
        }
        val pomModel = MyPomModelImpl(env)
        env.project.registerService(PomModel::class.java, pomModel)
        env.project.registerService(ModuleManager::class.java, EmptyModuleManager(env.project))
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(PsiAugmentProvider.EP_NAME.name, PsiAugmentProvider::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(TreeCopyHandler.EP_NAME.name, TreeCopyHandler::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
    }

    private fun setEnvForPython() {
        val disposable = Disposable {  }
        System.setProperty("idea.home.path", "lib/bin")
        val coreApplicationEnvironment = CoreApplicationEnvironment(disposable, false)
        env = CoreProjectEnvironment(disposable, coreApplicationEnvironment)
        (FileTypeRegistry.getInstance() as CoreFileTypeRegistry).registerFileType(PythonFileType.INSTANCE, "py")
        env.project.registerService(PyAstElementGenerator::class.java, PyElementGeneratorImpl(env.project))
        coreApplicationEnvironment.registerParserDefinition(PythonLanguage.INSTANCE, PythonParserDefinition())
        env.project.extensionArea.registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME.name, PythonDialectsTokenSetContributor::class.java.name, ExtensionPoint.Kind.INTERFACE, false)
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME.name, PythonDialectsTokenSetContributor::class.java.name, ExtensionPoint.Kind.INTERFACE, false)
        env.addProjectExtension(PythonDialectsTokenSetContributor.EP_NAME, PythonTokenSetContributor())
        env.addProjectExtension(PythonDialectsTokenSetContributor.EP_NAME, PyDocstringTokenSetContributor())
        coreApplicationEnvironment.registerApplicationService(PyPsiFacade::class.java, PyPsiFacadeImpl(env.project));
        coreApplicationEnvironment.registerApplicationService(PyElementTypesFacade::class.java, PyElementTypesFacadeImpl());
        coreApplicationEnvironment.registerApplicationService(PyLanguageFacade::class.java, PyLanguageFacadeImpl());
        env.project.registerService(TreeAspect::class.java, TreeAspect())

        class MyPomModelImpl(env: CoreProjectEnvironment) : PomModelImpl(env.project) {
            override fun runTransaction(pt: PomTransaction) = pt.run()
        }
        val pomModel = MyPomModelImpl(env)
        env.project.registerService(PomModel::class.java, pomModel)
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(PsiAugmentProvider.EP_NAME.name, PsiAugmentProvider::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
        ApplicationManager.getApplication().extensionArea.registerExtensionPoint(TreeCopyHandler.EP_NAME.name, TreeCopyHandler::class.java.name, ExtensionPoint.Kind.INTERFACE, true)
    }

    fun getPSIForText(text: String, generateCtx: Boolean = true): KtFile {
        //Save to tmp
        val path = "tmp/tmp.kt"
        File(path).writeText(text)
        return getPSIForFile(path)
    }

    fun getPSIForFile(path: String): KtFile {
        TODO("")
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
                FuzzingConf.pathToOwaspJar
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