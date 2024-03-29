package com.spbpu.bbfinfrastructure.psicreator.util

import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

/**
 * Created by akhin on 7/5/16.
 */

object PathUtilEx {
    private val NO_PATH = File("<no_path>")
    private val NO_VERSION = "no.version.at.all"
    private val NO_SHIT = Pair(
        NO_PATH,
        NO_VERSION
    )

    private val KOMPILER_RE = Regex("kotlin-compiler-(.*)\\.jar")

    fun getCompilerPathForCompilerJar(): Pair<File, String> {
        val jar = PathUtil.pathUtilJar

        if (!jar.exists()) return NO_SHIT

        val jarName = jar.name

        return KOMPILER_RE.matchEntire(jarName)?.let {
            Pair(jar.parentFile.parentFile.parentFile, it.groups[1]!!.value)
        } ?: NO_SHIT
    }

    fun getKotlinPathsForCompiler(): List<File> {
        val (path, version) = getCompilerPathForCompilerJar()

        if (NO_PATH == path) return arrayListOf()

        return path.walkTopDown()
                .filter { it.isFile && "jar" == it.extension }
                .filter { it.nameWithoutExtension.endsWith(version) }
                .toList()
    }
}
