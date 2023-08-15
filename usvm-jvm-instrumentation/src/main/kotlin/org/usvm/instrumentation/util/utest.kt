package org.usvm.instrumentation.util

import org.jacodb.api.JcField
import org.usvm.instrumentation.testcase.descriptor.UTestEnumValueDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor

fun UTestObjectDescriptor.getAllFields(): Set<Pair<JcField, UTestValueDescriptor>> {
    val res = mutableSetOf<Pair<JcField, UTestValueDescriptor>>()
    val deque = ArrayDeque<Pair<JcField, UTestValueDescriptor>>()
    deque.addAll(fields.entries.map { it.key to it.value })
    while (deque.isNotEmpty()) {
        val (field, value) = deque.removeFirst()
        res.add(field to value)
        when (value) {
            is UTestEnumValueDescriptor -> {
                if (!res.contains(field to value)) {
                    deque.addAll(value.fields.entries.map { it.key to it.value })
                }
            }
            is UTestObjectDescriptor -> {
                if (!res.contains(field to value)) {
                    deque.addAll(value.fields.entries.map { it.key to it.value })
                }
            }
            else -> {}
        }
    }
    return res
}