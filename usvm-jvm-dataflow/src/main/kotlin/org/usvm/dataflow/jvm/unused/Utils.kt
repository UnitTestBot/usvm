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

package org.usvm.dataflow.jvm.unused

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBranchingInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcLocal
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcTerminatingInst
import org.usvm.dataflow.ifds.AccessPath
import org.usvm.dataflow.util.Traits

internal fun AccessPath.isUsedAt(
    traits: Traits<CommonMethod, CommonInst>,
    expr: CommonExpr,
): Boolean = with(traits) {
    return getValues(expr).any {
        convertToPathOrNull(it) == this@isUsedAt
    }
}

internal fun AccessPath.isUsedAt(
    traits: Traits<CommonMethod, CommonInst>,
    inst: CommonInst,
): Boolean = with(traits) {
    val callExpr = getCallExpr(inst)

    if (callExpr != null) {
        // Don't count constructor calls as usages
        if (callExpr is JcSpecialCallExpr
            && callExpr.method.method.isConstructor
            && isUsedAt(traits, callExpr.instance)
        ) {
            return false
        }

        return isUsedAt(traits, callExpr)
    }
    if (inst is JcAssignInst) {
        if (inst.lhv is JcArrayAccess && isUsedAt(traits, inst.lhv)) {
            return true
        }
        return isUsedAt(traits, inst.rhv) && (inst.lhv !is JcLocal || inst.rhv !is JcLocal)
    }
    if (inst is JcTerminatingInst || inst is JcBranchingInst) {
        inst as JcInst
        return inst.operands.any { isUsedAt(traits, it) }
    }
    return false
}
