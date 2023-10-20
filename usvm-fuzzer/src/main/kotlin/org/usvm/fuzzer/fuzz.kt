import kotlinx.coroutines.runBlocking
import org.jacodb.api.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.usvm.fuzzer.Fuzzer
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.ArrayList

fun main() {
    val testingJars =
        "usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation-test.jar"
    val testingClassPath = testingJars.split(":").map { File(it) }
    val testingClassloader = URLClassLoader(arrayOf(Paths.get(testingJars).toUri().toURL()))
    val jcClasspath = initJcdb(testingClassPath)
    val targetClass = jcClasspath.findClass("example.A")
    println(targetClass)
    val targetMethod = targetClass.declaredMethods.find { it.name == "fuzz" }!!
    val fuzzer = Fuzzer(targetMethod, listOf(testingJars), testingClassloader)
    val res =
        runBlocking {
            fuzzer.fuzz()
        }
    println("RES = $res")
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
