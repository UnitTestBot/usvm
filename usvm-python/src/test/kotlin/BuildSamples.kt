import org.utbot.python.newtyping.mypy.MypyBuildDirectory
import org.utbot.python.newtyping.mypy.readMypyAnnotationStorageAndInitialErrors
import java.io.File

fun main(args: Array<String>) {
    val inputPath = args[0]
    val requiredPath = args[1]
    val pythonPath = args[2]
    val root = File(requiredPath)
    root.mkdirs()
    val mypyBuildDir = MypyBuildDirectory(root, setOf(inputPath))
    val files = File(inputPath).listFiles()!!.map { it!! }
    val modules = files.map { it.name.removeSuffix(".py") }
    readMypyAnnotationStorageAndInitialErrors(pythonPath, files.map { it.canonicalPath }, modules, mypyBuildDir, isolated = true)
}