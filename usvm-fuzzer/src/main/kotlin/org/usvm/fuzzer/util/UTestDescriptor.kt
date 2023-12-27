package org.usvm.fuzzer.util

import org.jacodb.api.JcField
import org.usvm.instrumentation.testcase.descriptor.UTestEnumValueDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import java.util.Stack

fun UTestValueDescriptor.getPossiblePathsToField(jcField: JcField): List<List<JcField>> {
    val res = ArrayList<List<JcField>>()
    dfsFieldSearch(this, jcField, HashSet(), ArrayList(), res)
    return res
}

private fun dfsFieldSearch(
    curDescriptor: UTestValueDescriptor,
    targetField: JcField,
    visited: MutableSet<UTestValueDescriptor>,
    path: MutableList<JcField>,
    res: MutableList<List<JcField>>
) {
    visited.add(curDescriptor)
    val fields =
        when (curDescriptor) {
            is UTestObjectDescriptor -> curDescriptor.fields
            is UTestEnumValueDescriptor -> curDescriptor.fields
            else -> mapOf()
        }
    for ((f, descriptor) in fields) {
        if (f == targetField) {
            res.add(path + listOf(f))
        }
        if (descriptor is UTestObjectDescriptor || descriptor is UTestEnumValueDescriptor && !visited.contains(descriptor)) {
            path.add(f)
            dfsFieldSearch(descriptor, targetField, visited, path, res)
        }
    }
    path.removeLastOrNull()
}