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
import org.jacodb.api.jvm.ext.methods
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.dataflow.jvm.ifds.SingletonUnitResolver
import org.usvm.dataflow.jvm.unused.UnusedVariableManager
import org.usvm.dataflow.jvm.util.JcTraits
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

@EnabledIfEnvironmentVariable(named = "ENABLE_JVM_DATAFLOW_LONG_TESTS", matches = "true")
@TestInstance(PER_CLASS)
class IfdsUnusedTest : BaseAnalysisTest() {

    fun provideClassesForJuliet563(): Stream<Arguments> = provideClassesForJuliet(
        563, listOf(
            // Unused variables are already optimized out by cfg
            "unused_uninit_variable_",
            "unused_init_variable_int",
            "unused_init_variable_long",
            "unused_init_variable_String_",

            // Unused variable is generated by cfg (!!)
            "unused_value_StringBuilder_17",

            // Expected answers are strange, seems to be problem in tests
            "_12",

            // The variable isn't expected to be detected as unused actually
            "_81"
        )
    )

    @Disabled("See https://github.com/UnitTestBot/jacodb/issues/220")
    @ParameterizedTest
    @MethodSource("provideClassesForJuliet563")
    fun `test on Juliet's CWE 563`(className: String) {
        testSingleJulietClass(className) { method ->
            val unitResolver = SingletonUnitResolver
            val manager = with(JcTraits(cp)) {
                UnusedVariableManager(traits = this, graph, unitResolver)
            }
            manager.analyze(listOf(method), timeout = 30.seconds)
        }
    }

    @Test
    fun `test on specific Juliet instance`() {
        val className =
            "juliet.testcases.CWE563_Unused_Variable.CWE563_Unused_Variable__unused_init_variable_StringBuilder_01"
        val clazz = cp.findClass(className)
        val badMethod = clazz.methods.single { it.name == "bad" }
        val unitResolver = SingletonUnitResolver
        val manager = with(JcTraits(cp)) {
            UnusedVariableManager(traits = this, graph, unitResolver)
        }
        val sinks = manager.analyze(listOf(badMethod), timeout = 30.seconds)
        Assertions.assertTrue(sinks.isNotEmpty())
    }
}
