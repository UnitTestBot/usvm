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

package org.usvm.util

import org.usvm.model.TsAssignStmt
import org.usvm.model.TsEntity
import org.usvm.model.TsLocal
import org.usvm.model.TsMethod
import org.usvm.model.TsStmt

fun TsMethod.getDeclaredLocals(): Set<TsLocal> =
    cfg.stmts.mapNotNullTo(mutableSetOf()) {
        if (it is TsAssignStmt && it.lhv is TsLocal) {
            it.lhv
        } else {
            null
        }
    }

fun TsMethod.getLocals(): Set<TsLocal> {
    val result = mutableSetOf<TsLocal>()
    cfg.stmts.forEach { it.collectEntitiesTo(result) { it as? TsLocal } }
    return result
}

fun TsStmt.getLocals(): Set<TsLocal> {
    return collectEntitiesTo(mutableSetOf()) { it as? TsLocal }
}

fun TsEntity.getLocals(): Set<TsLocal> {
    return collectEntitiesTo(mutableSetOf()) { it as? TsLocal }
}
