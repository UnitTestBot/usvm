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

import NullAssumptionAnalysisExample
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcInstanceCallExpr
import org.jacodb.api.jvm.cfg.JcLocal
import org.jacodb.api.jvm.ext.findClass
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.usvm.dataflow.jvm.flow.NullAssumptionAnalysis

@TestInstance(PER_CLASS)
class NullabilityAssumptionAnalysisTest : BaseAnalysisTest() {

    @Test
    fun `null-assumption analysis should work`() {
        val clazz = cp.findClass<NullAssumptionAnalysisExample>()
        with(clazz.findMethod("test1").flowGraph()) {
            val analysis = NullAssumptionAnalysis(this).also {
                it.run()
            }
            val sout = (instructions[0] as JcAssignInst).lhv as JcLocal
            val a = ((instructions[3] as JcAssignInst).rhv as JcInstanceCallExpr).instance

            assertTrue(analysis.isAssumedNonNullBefore(instructions[2], a))
            assertTrue(analysis.isAssumedNonNullBefore(instructions[0], sout))
        }
    }

    @Test
    fun `null-assumption analysis should work 2`() {
        val clazz = cp.findClass<NullAssumptionAnalysisExample>()
        with(clazz.findMethod("test2").flowGraph()) {
            val analysis = NullAssumptionAnalysis(this).also {
                it.run()
            }
            val sout = (instructions[0] as JcAssignInst).lhv as JcLocal
            val a = ((instructions[3] as JcAssignInst).rhv as JcInstanceCallExpr).instance
            val x = (instructions[5] as JcAssignInst).lhv as JcLocal

            assertTrue(analysis.isAssumedNonNullBefore(instructions[2], a))
            assertTrue(analysis.isAssumedNonNullBefore(instructions[0], sout))
            analysis.isAssumedNonNullBefore(instructions[5], x)
        }
    }

    private fun JcClassOrInterface.findMethod(name: String): JcMethod = declaredMethods.first { it.name == name }

}
