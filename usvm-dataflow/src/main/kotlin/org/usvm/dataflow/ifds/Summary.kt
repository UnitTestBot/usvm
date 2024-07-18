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

package org.usvm.dataflow.ifds

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentHashMap

/**
 * A common interface for anything that should be remembered
 * and used after the analysis of some unit is completed.
 */
interface Summary<out Method : CommonMethod> {
    val method: Method
}

interface SummaryEdge<out Fact, out Statement : CommonInst> : Summary<CommonMethod> {

    val edge: Edge<Fact, Statement>

    override val method: CommonMethod
        get() = edge.method
}

interface Vulnerability<out Fact, out Statement : CommonInst> : Summary<CommonMethod> {

    val message: String
    val sink: Vertex<Fact, Statement>

    override val method: CommonMethod
        get() = sink.method
}

/**
 * Contains summaries for many methods and allows to update them and subscribe for them.
 */
class SummaryStorageWithFlows<T : Summary<*>> {
    private val summaries = ConcurrentHashMap<CommonMethod, MutableSet<T>>()
    private val outFlows = ConcurrentHashMap<CommonMethod, MutableSharedFlow<T>>()

    /**
     * @return a list with all methods for which there are some summaries.
     */
    val knownMethods: List<CommonMethod>
        get() = summaries.keys.toList()

    private fun getFlow(method: CommonMethod): MutableSharedFlow<T> {
        return outFlows.computeIfAbsent(method) {
            MutableSharedFlow(replay = Int.MAX_VALUE)
        }
    }

    /**
     * Adds a new [fact] to the storage.
     */
    fun add(fact: T) {
        val isNew = summaries.computeIfAbsent(fact.method) { ConcurrentHashMap.newKeySet() }.add(fact)
        if (isNew) {
            val flow = getFlow(fact.method)
            check(flow.tryEmit(fact))
        }
    }

    /**
     * @return a flow with all facts summarized for the given [method].
     * Already received facts, along with the facts that will be sent to this storage later,
     * will be emitted to the returned flow.
     */
    fun getFacts(method: CommonMethod): SharedFlow<T> {
        return getFlow(method)
    }

    /**
     * @return a list will all facts summarized for the given [method] so far.
     */
    fun getCurrentFacts(method: CommonMethod): List<T> {
        return getFacts(method).replayCache
    }
}

class SummaryStorageWithProducers<T : Summary<*>>(
    private val isConcurrent: Boolean = true,
) {
    private val summaries = ConcurrentHashMap<CommonMethod, MutableSet<T>>()
    private val producers = ConcurrentHashMap<CommonMethod, Producer<T>>()

    val knownMethods: Collection<CommonMethod>
        get() = summaries.keys

    private fun getProducer(method: CommonMethod): Producer<T> {
        return producers.computeIfAbsent(method) {
            if (isConcurrent) {
                ConcurrentProducer()
            } else {
                SyncProducer()
            }
        }
    }

    fun add(fact: T) {
        val isNew = summaries.computeIfAbsent(fact.method) { ConcurrentHashMap.newKeySet() }.add(fact)
        if (isNew) {
            val producer = getProducer(fact.method)
            producer.produce(fact)
        }
    }

    fun subscribe(method: CommonMethod, handler: (T) -> Unit) {
        val producer = getProducer(method)
        producer.subscribe(handler)
    }
}
