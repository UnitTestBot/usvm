/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.ifds

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UnitResolver
import org.usvm.dataflow.ifds.UnitType

data class MethodUnit(val method: EtsMethodSignature) : UnitType {
    override fun toString(): String {
        return "MethodUnit(${method.name})"
    }
}

data class ClassUnit(val clazz: EtsClassSignature) : UnitType {
    override fun toString(): String {
        return "ClassUnit(${clazz.name})"
    }
}

// TODO: PackageUnit
// data class PackageUnit(val packageName: String) : UnitType {
//     override fun toString(): String {
//         return "PackageUnit($packageName)"
//     }
// }

fun interface EtsUnitResolver : UnitResolver<EtsMethod>

val MethodUnitResolver = EtsUnitResolver { method ->
    MethodUnit(method.signature)
}

val ClassUnitResolver = EtsUnitResolver { method ->
    ClassUnit(method.signature.enclosingClass)
}

// TODO: PackageUnitResolver
// val PackageUnitResolver = EtsUnitResolver { method ->
//     PackageUnit(method.enclosingClass.packageName)
// }

val SingletonUnitResolver = EtsUnitResolver {
    SingletonUnit
}
