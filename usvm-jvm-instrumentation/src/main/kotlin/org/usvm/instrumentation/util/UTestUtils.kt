package org.usvm.instrumentation.util

import org.jacodb.api.jvm.JcField
import org.usvm.instrumentation.testcase.descriptor.UTestEnumValueDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor

fun UTestObjectDescriptor.getAllFields(): Set<Pair<JcField, UTestValueDescriptor>> {
    val res = mutableSetOf<Pair<JcField, UTestValueDescriptor>>()
    val deque = dequeOf(fields.entries.map { it.key to it.value })
    while (deque.isNotEmpty()) {
        val fieldToDescriptor = deque.removeFirst()
        val fieldDescriptor = fieldToDescriptor.second
        res.add(fieldToDescriptor)
        when (fieldDescriptor) {
            is UTestEnumValueDescriptor -> {
                if (!res.contains(fieldToDescriptor)) {
                    deque.addAll(fieldDescriptor.fields.entries.map { it.key to it.value })
                }
            }
            is UTestObjectDescriptor -> {
                if (!res.contains(fieldToDescriptor)) {
                    deque.addAll(fieldDescriptor.fields.entries.map { it.key to it.value })
                }
            }
            else -> {}
        }
    }
    return res
}