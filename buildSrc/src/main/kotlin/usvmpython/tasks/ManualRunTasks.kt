package usvmpython.tasks

import org.gradle.api.Project
import usvmpython.*
import java.io.File

fun Project.pythonEnvironmentVariables(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val cpythonBuildPath = getCPythonBuildPath()
    val adapterPath = getCPythonAdapterBuildPath()

    result["PYTHONHOME"] = cpythonBuildPath.canonicalPath

    if (!isWindows) {
        result["LD_LIBRARY_PATH"] = "$cpythonBuildPath/lib:$adapterPath"
        result["LD_PRELOAD"] = "$cpythonBuildPath/lib/libpython3.so"
    } else {
        val pythonDllsPath: String = File(cpythonBuildPath, "DLLs").canonicalPath
        result["PATH"] = "$cpythonBuildPath;$pythonDllsPath"
    }

    return result
}

fun Project.javaArgumentsForPython(debugLog: Boolean): List<String> {
    val samplesSourceDir = getSamplesSourceDir()
    val approximationsDir = getApproximationsDir()
    val samplesBuildDir = getSamplesBuildDir()
    val adapterPath = getCPythonAdapterBuildPath()
    val pythonPath = getPythonBinaryPath()

    val result = mutableListOf(
        "-Dsamples.build.path=${samplesBuildDir.canonicalPath}",
        "-Dsamples.sources.path=${samplesSourceDir.canonicalPath}",
        "-Dapproximations.path=${approximationsDir.canonicalPath}",
        "-Djava.library.path=$adapterPath",
        "-Dpython.binary.path=$pythonPath",
        "-Xss50m",
    )

    result += if (debugLog) {
        "-Dlogback.configurationFile=logging/logback-debug.xml"
    } else {
        "-Dlogback.configurationFile=logging/logback-info.xml"
    }

    // Uncomment this line for JNI checks
    // result += "-Xcheck:jni"

    return result
}