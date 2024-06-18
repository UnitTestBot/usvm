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

package org.usvm.dataflow.util

import org.usvm.dataflow.ifds.AccessPath
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.ElementAccessor
import org.usvm.dataflow.ifds.Runner
import org.usvm.dataflow.ifds.UniRunner
import org.usvm.dataflow.taint.TaintBidiRunner

fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this.value != other.value) {
        return false
    }
    return this.accesses.take(other.accesses.size) == other.accesses
}

internal fun AccessPath.removeTrailingElementAccessors(): AccessPath {
    var index = accesses.size
    while (index > 0 && accesses[index - 1] is ElementAccessor) {
        index--
    }
    return AccessPath(value, accesses.subList(0, index))
}

fun Runner<*, *, *>.getPathEdges(): Set<Edge<*, *>> = when (this) {
    is UniRunner<*, *, *, *> -> pathEdges
    is TaintBidiRunner<*, *> -> forwardRunner.getPathEdges() + backwardRunner.getPathEdges()
    else -> error("Cannot extract pathEdges for $this")
}
