package org.usvm.machine

import org.usvm.ULValue
import org.usvm.USort

class JcStaticFieldRef<Field>(fieldSort: USort, val field: Field) : ULValue(fieldSort)
