/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.jvm.impl

import juliet.support.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.methods
import org.jacodb.impl.JcRamErsSettings
import org.jacodb.impl.features.Builders
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.jacodb
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.provider.Arguments
import org.usvm.dataflow.ifds.Vulnerability
import org.usvm.dataflow.jvm.graph.JcApplicationGraph
import org.usvm.dataflow.jvm.graph.newApplicationGraphForAnalysis
import java.io.File
import java.util.stream.Stream
import kotlin.streams.asStream

private val logger = mu.KotlinLogging.logger {}

abstract class BaseAnalysisTest(
    private val configFileName: String = "config_small.json"
) {
    val allClasspath: List<File>
        get() = classpath.map { File(it) }

    private val classpath: List<String>
        get() {
            val classpath = System.getProperty("java.class.path")
            return classpath.split(File.pathSeparatorChar).toList()
        }

    val db: JcDatabase = runBlocking {
        jacodb {
            persistenceImpl(JcRamErsSettings)
            loadByteCode(allClasspath)
            useProcessJavaRuntime()
            keepLocalVariableNames()
            installFeatures(Usages, Builders, InMemoryHierarchy)
        }.also {
            it.awaitBackgroundJobs()
        }
    }

    val cp: JcClasspath = runBlocking {
        val configResource = this.javaClass.getResourceAsStream("/$configFileName")
        if (configResource != null) {
            val configJson = configResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + UnknownClasses)
        } else {
            db.classpath(allClasspath, listOf(UnknownClasses))
        }
    }

    @Suppress("JUnitMalformedDeclaration") // all tests use @TestInstance(PER_CLASS)
    @AfterAll
    fun close() {
        cp.close()
        db.close()
    }

    fun provideClassesForJuliet(
        cweNum: Int,
        cweSpecificBans: List<String> = emptyList(),
    ): Stream<Arguments> =
        getJulietClasses(cweNum, cweSpecificBans)
            .map { Arguments.of(it) }
            .asStream()

    private fun getJulietClasses(
        cweNum: Int,
        cweSpecificBans: List<String> = emptyList(),
    ): Sequence<String> = runBlocking {
        val hierarchyExt = cp.hierarchyExt()
        val baseClass = cp.findClass<AbstractTestCase>()
        hierarchyExt.findSubClasses(baseClass, false)
            .map { it.name }
            .filter { it.contains("CWE${cweNum}_") }
            .filterNot { className -> (commonJulietBans + cweSpecificBans).any { className.contains(it) } }
            .sorted()
    }

    private val commonJulietBans = listOf(
        // TODO: containers not supported
        "_72", "_73", "_74",

        // TODO/Won't fix(?): dead parts of switches shouldn't be analyzed
        "_15",

        // TODO/Won't fix(?): passing through channels not supported
        "_75",

        // TODO/Won't fix(?): constant private/static methods not analyzed
        "_11", "_08",

        // TODO/Won't fix(?): unmodified non-final private variables not analyzed
        "_05", "_07",

        // TODO/Won't fix(?): unmodified non-final static variables not analyzed
        "_10", "_14",
    )

    open val graph: JcApplicationGraph by lazy {
        runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
    }

    protected fun testSingleJulietClass(
        className: String,
        findSinks: (JcMethod) -> List<Vulnerability<*, JcInst>>,
    ) {
        logger.info { className }

        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val goodMethod = clazz.methods.single { it.name == "good" }

        logger.info { "Searching for sinks in BAD method: $badMethod" }
        val badIssues = findSinks(badMethod)
        logger.info { "Total ${badIssues.size} issues in BAD method" }
        for (issue in badIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(badIssues.isNotEmpty()) { "Must find some sinks in 'bad' for $className" }

        logger.info { "Searching for sinks in GOOD method: $goodMethod" }
        val goodIssues = findSinks(goodMethod)
        logger.info { "Total ${goodIssues.size} issues in GOOD method" }
        for (issue in goodIssues) {
            logger.debug { "  - $issue" }
        }
        assertTrue(goodIssues.isEmpty()) { "Must NOT find any sinks in 'good' for $className" }
    }
}
