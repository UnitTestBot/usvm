import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.usvm.fuzzer.fuzzing.Fuzzer
import org.usvm.fuzzer.types.JcClassTable
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.instrumentation.JcExtendedRuntimeTraceInstrumenterFactory
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

fun main() {
//    val testingJars =
//        "usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation-test.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/guava-26.0-jre.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/./guava/guava/target/dependency/checker-qual-2.5.2.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/error_prone_annotations-2.1.3.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/j2objc-annotations-1.1.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/jsr305-3.0.2.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/animal-sniffer-annotations-1.14.jar"
    val testingJars = "/home/zver/IdeaProjects/usvm/usvm-fuzzer/build/libs/usvm-fuzzer-test.jar"
    val testingClassPath = testingJars.split(":").map { File(it) }
    val testingClassloader = URLClassLoader(
        /* p0 = */ testingJars.split(":").map { Paths.get(it).toUri().toURL() }.toTypedArray(),
        /* p1 = */ Fuzzer::class.java.classLoader.parent
    )
    val jcClasspath = initJcdb(testingClassPath)
    JcClassTable.initClasses(jcClasspath)
    val targetClass = jcClasspath.findClass("org.usvm.fuzzer.test.Strings")
//    val targetClass = jcClasspath.findClass("example.fuzz.Simple")
    targetClass.toType()
    println(targetClass)
    val runner = UTestConcreteExecutor(
        JcExtendedRuntimeTraceInstrumenterFactory::class,
        testingClassPath.map { it.absolutePath },
        jcClasspath,
        InstrumentationModuleConstants.testExecutionTimeout
    )

    val methodsToFilterNot = listOf("<init>", "<clinit>")
    val methodsToFilter = listOf<String>("test")
    val filter = { method: JcMethod -> methodsToFilter.isEmpty() || methodsToFilter.any { method.name.contains(it) } }
    val filterNot = { method: JcMethod -> methodsToFilterNot.any { method.name.contains(it) } }
    for (targetMethod in targetClass.declaredMethods.filter(filter).filterNot(filterNot)) {
        println("EXEC METHOD ${targetMethod.name}")
        val fuzzer = Fuzzer(targetMethod, listOf(testingJars), testingClassloader, runner, 15.seconds)
        runBlocking {
            fuzzer.fuzz()
        }
    }
    val classInsts = targetClass.declaredMethods.flatMap { it.instList }
    val classInstsSize = classInsts.map { it.lineNumber }.toSet().size
    val cov = Cov.coveredStatements.filter { it in classInsts }.map { it.location.lineNumber }.toSet()
    println("COVERED ${cov.size} from $classInstsSize lines (${cov.size/classInstsSize.toDouble()*100.0}%)")
    runner.close()
}

object Cov{
    val coveredStatements = hashSetOf<JcInst>()
}

fun initJcdb(testingClassPath: List<File>) = runBlocking {
    val db = jacodb {
        //useProcessJavaRuntime()
        loadByteCode(testingClassPath)
        installFeatures(InMemoryHierarchy, Usages)
        jre = File(InstrumentationModuleConstants.pathToJava)
        //persistent(location = "/home/.usvm/jcdb.db", clearOnStart = false)
    }
    db.classpath(testingClassPath) ?: error("Can't find jcClasspath")
}
