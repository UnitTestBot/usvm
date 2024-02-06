package org.usvm.fuzzer.generators

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorRepository
import org.usvm.fuzzer.generator.random.FuzzerRandomNormalDistribution
import org.usvm.fuzzer.helpers.JcdbConstantsCollector
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import kotlin.random.Random

open class GeneratorTest {


    companion object {
        lateinit var testJarPath: List<String>
        lateinit var jcClasspath: JcClasspath
        lateinit var generatorRepository: GeneratorRepository
        lateinit var userClassLoader: ClassLoader

        fun init() {
            val cp = testJarPath.map { File(it) }
            val db = runBlocking {
                jacodb {
                    loadByteCode(cp)
                    installFeatures(InMemoryHierarchy)
                    jre = File(InstrumentationModuleConstants.pathToJava)
                }
            }
            jcClasspath = runBlocking {
                db.classpath(cp)
            }
            generatorRepository = GeneratorRepository()
            val generatorContext = GeneratorContext(
                mapOf(), generatorRepository,
                FuzzerRandomNormalDistribution(0, 0.0, 50.0),
                jcClasspath,
                userClassLoader,
                mutableMapOf()
            )
            generatorRepository.registerGeneratorContext(generatorContext)
        }
    }

}