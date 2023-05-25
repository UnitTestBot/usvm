package org.usvm.instrumentation.util.config

import java.nio.file.Path

val Config.outputDirectory: Path get() = getPathValue("kex", "outputDir")!!
//
//val Config.instrumentedCodeDirectory: Path
//    get() {
//        val instrumentedDirName = getStringValue("output", "instrumentedDir", "instrumented")
//        val instrumentedCodeDir = outputDirectory.resolve(instrumentedDirName).toAbsolutePath()
//        if (!getBooleanValue("debug", "saveInstrumentedCode", false)) {
//            deleteOnExit(instrumentedCodeDir)
//        }
//        return instrumentedCodeDir
//    }
//
//val Config.compiledCodeDirectory: Path
//    get() {
//        val compiledCodeDirName = getStringValue("compile", "compileDir", "compiled")
//        val compiledCodeDir = outputDirectory.resolve(compiledCodeDirName).toAbsolutePath()
//        if (!getBooleanValue("debug", "saveCompiledCode", false)) {
//            deleteOnExit(compiledCodeDir)
//        }
//        return compiledCodeDir
//    }
//
//val Config.testcaseDirectory: Path
//    get() {
//        val testcaseDirName = getPathValue("testGen", "testsDir", "tests")
//        return outputDirectory.resolve(testcaseDirName).toAbsolutePath()
//    }
//
//val Config.runtimeDepsPath: Path?
//    get() = getPathValue("kex", "runtimeDepsPath")
//
//val Config.libPath: Path?
//    get() = getStringValue("kex", "libPath")?.let {
//        runtimeDepsPath?.resolve(it)
//    }