package org.usvm.machine

import org.usvm.ULValue
import org.usvm.USort

// TODO: unused now, because we need to extend UMemoryBase, which is not properly supported yet
class JcStaticFieldRef<Field>(fieldSort: USort, val field: Field) : ULValue(fieldSort)
