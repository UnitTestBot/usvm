import kotlinx.coroutines.runBlocking
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.usvm.fuzzer.Fuzzer
import org.usvm.fuzzer.types.JcClassTable
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.ArrayList

fun main() {
    val testingJars =
        "usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation-test.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/guava-26.0-jre.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/./guava/guava/target/dependency/checker-qual-2.5.2.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/error_prone_annotations-2.1.3.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/j2objc-annotations-1.1.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/jsr305-3.0.2.jar:/home/zver/IdeaProjects/usvm/usvm-fuzzer/src/main/resources/guava/guava/target/dependency/animal-sniffer-annotations-1.14.jar"
    val testingClassPath = testingJars.split(":").map { File(it) }
    val testingClassloader = URLClassLoader(arrayOf(Paths.get(testingJars).toUri().toURL()))
    val jcClasspath = initJcdb(testingClassPath)
    JcClassTable.initClasses(jcClasspath)
    val targetClass = jcClasspath.findClass("com.google.common.primitives.Shorts")
    println(targetClass)
    val targetMethod = targetClass.declaredMethods.find { it.name == "join" }!!
    val fuzzer = Fuzzer(targetMethod, listOf(testingJars), testingClassloader)
    val res =
        runBlocking {
            fuzzer.fuzz()
        }
    fuzzer.runner.close()
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
