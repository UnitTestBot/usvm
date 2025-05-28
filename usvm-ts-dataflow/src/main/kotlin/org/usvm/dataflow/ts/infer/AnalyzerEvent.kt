package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.dataflow.ifds.Vertex

sealed interface AnalyzerEvent

data class ForwardSummaryAnalyzerEvent(
    val method: EtsMethod,
    val initialVertex: Vertex<ForwardTypeDomainFact, EtsStmt>,
    val exitVertex: Vertex<ForwardTypeDomainFact, EtsStmt>,
) : AnalyzerEvent {
    val initialFact get() = initialVertex.fact
    val exitFact get() = exitVertex.fact
}

data class BackwardSummaryAnalyzerEvent(
    val method: EtsMethod,
    val initialVertex: Vertex<BackwardTypeDomainFact, EtsStmt>,
    val exitVertex: Vertex<BackwardTypeDomainFact, EtsStmt>,
) : AnalyzerEvent {
    val initialFact get() = initialVertex.fact
    val exitFact get() = exitVertex.fact
}
