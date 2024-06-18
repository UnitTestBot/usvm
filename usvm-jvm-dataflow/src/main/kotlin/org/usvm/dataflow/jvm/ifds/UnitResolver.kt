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

@file:Suppress("FunctionName")

package org.usvm.dataflow.jvm.ifds

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.packageName
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UnitResolver
import org.usvm.dataflow.ifds.UnitType

data class MethodUnit(val method: JcMethod) : UnitType {
    override fun toString(): String {
        return "MethodUnit(${method.name})"
    }
}

data class ClassUnit(val clazz: JcClassOrInterface) : UnitType {
    override fun toString(): String {
        return "ClassUnit(${clazz.simpleName})"
    }
}

data class PackageUnit(val packageName: String) : UnitType {
    override fun toString(): String {
        return "PackageUnit($packageName)"
    }
}

fun interface JcUnitResolver : UnitResolver<JcMethod>

val MethodUnitResolver = JcUnitResolver { method ->
    MethodUnit(method)
}

private val ClassUnitResolverWithNested = JcUnitResolver { method ->
    val clazz = generateSequence(method.enclosingClass) { it.outerClass }.last()
    ClassUnit(clazz)
}
private val ClassUnitResolverWithoutNested = JcUnitResolver { method ->
    val clazz = method.enclosingClass
    ClassUnit(clazz)
}

fun ClassUnitResolver(includeNested: Boolean) =
    if (includeNested) {
        ClassUnitResolverWithNested
    } else {
        ClassUnitResolverWithoutNested
    }

val PackageUnitResolver = JcUnitResolver { method ->
    PackageUnit(method.enclosingClass.packageName)
}

val SingletonUnitResolver = JcUnitResolver {
    SingletonUnit
}
