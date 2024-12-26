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

import org.jacodb.api.jvm.ext.findClass
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.usvm.dataflow.jvm.ifds.SingletonUnitResolver
import org.usvm.dataflow.jvm.npe.jcNpeManager
import org.usvm.dataflow.jvm.taint.jcTaintManager
import org.usvm.dataflow.jvm.unused.UnusedVariableManager
import org.usvm.dataflow.jvm.util.JcTraits
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

@EnabledIfEnvironmentVariable(named = "ENABLE_JVM_DATAFLOW_LONG_TESTS", matches = "true")
@TestInstance(PER_CLASS)
class JodaDateTimeAnalysisTest : BaseAnalysisTest() {

    @Test
    fun `test taint analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = jcTaintManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test NPE analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = jcNpeManager(graph, unitResolver)
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Vulnerabilities found: ${sinks.size}" }
    }

    @Test
    fun `test unused variables analysis`() {
        val clazz = cp.findClass<DateTime>()
        val methods = clazz.declaredMethods
        val unitResolver = SingletonUnitResolver
        val manager = with(JcTraits(cp)) {
            UnusedVariableManager(traits = this, graph, unitResolver)
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Unused variables found: ${sinks.size}" }
    }
}
