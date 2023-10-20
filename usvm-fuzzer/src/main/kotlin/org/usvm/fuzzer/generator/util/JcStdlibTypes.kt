package org.usvm.fuzzer.generator.util

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.ext.toType


fun JcClasspath.arrayListType(): JcType {
    return findClassOrNull("java.util.ArrayList")!!.toType()
}