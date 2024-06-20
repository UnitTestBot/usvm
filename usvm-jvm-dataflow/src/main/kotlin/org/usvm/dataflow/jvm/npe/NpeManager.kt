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

package org.usvm.dataflow.jvm.npe

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.usvm.dataflow.ifds.UniRunner
import org.usvm.dataflow.ifds.UnitResolver
import org.usvm.dataflow.ifds.UnitType
import org.usvm.dataflow.ifds.UnknownUnit
import org.usvm.dataflow.jvm.ifds.JcUnitResolver
import org.usvm.dataflow.jvm.util.JcTraits
import org.usvm.dataflow.taint.TaintManager
import org.usvm.dataflow.taint.TaintRunner
import org.usvm.dataflow.taint.TaintZeroFact

private val logger = mu.KotlinLogging.logger {}

context(JcTraits)
class NpeManager(
    graph: ApplicationGraph<JcMethod, JcInst>,
    unitResolver: UnitResolver<JcMethod>,
    private val getConfigForMethod: (JcMethod) -> List<TaintConfigurationItem>?,
) : TaintManager<JcMethod, JcInst>(graph, unitResolver, useBidiRunner = false, getConfigForMethod) {
    override fun newRunner(
        unit: UnitType,
    ): TaintRunner<JcMethod, JcInst> {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        val analyzer = NpeAnalyzer(graph, getConfigForMethod)
        val runner = UniRunner(
            graph = graph,
            analyzer = analyzer,
            manager = this@NpeManager,
            unitResolver = unitResolver,
            unit = unit,
            zeroFact = TaintZeroFact
        )

        runnerForUnit[unit] = runner
        return runner
    }

    override fun addStart(method: JcMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        if (unit == UnknownUnit) return
        methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        // Note: DO NOT add deps here!
    }
}

fun jcNpeManager(
    graph: ApplicationGraph<JcMethod, JcInst>,
    unitResolver: JcUnitResolver,
    getConfigForMethod: ((JcMethod) -> List<TaintConfigurationItem>?)? = null
): NpeManager = with(JcTraits) {
    val config: (JcMethod) -> List<TaintConfigurationItem>? = getConfigForMethod ?: run {
        val taintConfigurationFeature = (graph.project as JcClasspath).features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }

        return@run { method: JcMethod -> taintConfigurationFeature?.getConfigForMethod(method) }
    }

    NpeManager(graph, unitResolver, config)
}
