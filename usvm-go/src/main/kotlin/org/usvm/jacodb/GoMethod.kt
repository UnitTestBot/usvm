package org.usvm.jacodb

import org.jacodb.api.core.CoreMethod

interface GoMethod : CoreMethod<GoInst>, GoValue {
    val metName: String
    val blocks: List<GoBasicBlock>
}
