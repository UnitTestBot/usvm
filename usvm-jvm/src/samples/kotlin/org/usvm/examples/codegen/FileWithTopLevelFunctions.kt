package org.usvm.examples.codegen

class KotlinCustomClass

fun topLevelSum(a: Int, b: Int): Int {
    return a + b
}

fun Int.extensionOnBasicType(other: Int): Int {
    return this + other
}

fun KotlinCustomClass.extensionOnCustomClass(other: KotlinCustomClass): Boolean {
    return this === other
}