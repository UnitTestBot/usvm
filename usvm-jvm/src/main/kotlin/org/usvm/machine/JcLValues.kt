package org.usvm.machine

import org.jacodb.api.JcField
import org.usvm.ULValue
import org.usvm.USort

// TODO: unused now, because we need to extend UMemoryBase, which is not properly supported yet
class JcStaticFieldRef(fieldSort: USort, val field: JcField) : ULValue(fieldSort)
