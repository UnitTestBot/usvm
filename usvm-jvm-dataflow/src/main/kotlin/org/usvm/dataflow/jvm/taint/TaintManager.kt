package org.usvm.dataflow.jvm.taint

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.usvm.dataflow.jvm.ifds.JcUnitResolver
import org.usvm.dataflow.jvm.util.JcTraits
import org.usvm.dataflow.taint.TaintManager

fun jcTaintManager(
    graph: ApplicationGraph<JcMethod, JcInst>,
    unitResolver: JcUnitResolver,
    useBidiRunner: Boolean = false,
    getConfigForMethod: ((JcMethod) -> List<TaintConfigurationItem>?)? = null
): TaintManager<JcMethod, JcInst> = with(JcTraits) {
    val config: (JcMethod) -> List<TaintConfigurationItem>? = getConfigForMethod ?: run {
        val taintConfigurationFeature = (graph.project as JcClasspath).features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }

        return@run { method: JcMethod -> taintConfigurationFeature?.getConfigForMethod(method) }
    }

    TaintManager(graph, unitResolver, useBidiRunner, config)
}
