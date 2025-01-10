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

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonInst

data class Vertex<out Fact, out Statement : CommonInst>(
    val statement: Statement,
    val fact: Fact,
) {
    val method: CommonMethod
        get() = statement.method

    override fun toString(): String {
        return "$fact at $statement in $method"
    }

    private val cachedHashCode by lazy {
        fact.hashCode() * 31 + statement.hashCode()
    }

    override fun hashCode(): Int {
        return cachedHashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vertex<*, *>

        if (statement != other.statement) return false
        if (fact != other.fact) return false

        return true
    }
}
