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

import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstanceCallExpr
import org.jacodb.api.jvm.cfg.JcLengthExpr
import org.jacodb.api.jvm.cfg.values
import org.usvm.dataflow.ifds.AccessPath
import org.usvm.dataflow.ifds.minus
import org.usvm.dataflow.jvm.util.JcTraits
import org.usvm.dataflow.util.startsWith

context(JcTraits)
internal fun AccessPath?.isDereferencedAt(expr: JcExpr): Boolean {
    if (this == null) {
        return false
    }

    if (expr is JcInstanceCallExpr) {
        val instancePath = convertToPathOrNull(expr.instance)
        if (instancePath.startsWith(this)) {
            return true
        }
    }

    if (expr is JcLengthExpr) {
        val arrayPath = convertToPathOrNull(expr.array)
        if (arrayPath.startsWith(this)) {
            return true
        }
    }

    return expr.values
        .mapNotNull { convertToPathOrNull(it) }
        .any { (it - this)?.isNotEmpty() == true }
}

context(JcTraits)
internal fun AccessPath?.isDereferencedAt(inst: JcInst): Boolean {
    if (this == null) return false
    return inst.operands.any { isDereferencedAt(it) }
}
