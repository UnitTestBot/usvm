/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.usvm.dataflow.ts.infer.EtsTypeFact.AnyEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.ArrayEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.BooleanEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.Companion.mkIntersectionType
import org.usvm.dataflow.ts.infer.EtsTypeFact.Companion.mkUnionType
import org.usvm.dataflow.ts.infer.EtsTypeFact.FunctionEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.GuardedTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.IntersectionEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.NullEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.NumberEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.ObjectEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.StringEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.UndefinedEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.UnionEtsTypeFact
import org.usvm.dataflow.ts.infer.EtsTypeFact.UnknownEtsTypeFact
import org.usvm.dataflow.ts.util.toStringLimited

private val logger = KotlinLogging.logger {}

class TypeFactProcessor(
    private val scene: EtsScene,
) {
    private val classBySignature by lazy {
        scene.projectAndSdkClasses.associateByTo(hashMapOf()) { it.signature }
    }

    fun union(type: EtsTypeFact, other: EtsTypeFact): EtsTypeFact {
        if (type == other) return type

        return when {
            type is ObjectEtsTypeFact && other is ObjectEtsTypeFact -> union(type, other)
            type is ObjectEtsTypeFact && other is StringEtsTypeFact -> union(type, other)
            type is UnionEtsTypeFact -> union(type, other)
            type is IntersectionEtsTypeFact -> union(type, other)
            type is GuardedTypeFact -> union(type, other)
            other is UnionEtsTypeFact -> union(other, type)
            other is IntersectionEtsTypeFact -> union(other, type)
            other is GuardedTypeFact -> union(other, type)
            else -> mkUnionType(type, other)
        }
    }

    fun intersect(type: EtsTypeFact, other: EtsTypeFact?): EtsTypeFact? {
        if (other == null) return type

        if (type == other) return type

        if (other is UnknownEtsTypeFact) return type
        if (other is AnyEtsTypeFact) return other

        return when (type) {
            is UnknownEtsTypeFact -> other

            is AnyEtsTypeFact -> type

            is StringEtsTypeFact,
            is NumberEtsTypeFact,
            is BooleanEtsTypeFact,
            is NullEtsTypeFact,
            is UndefinedEtsTypeFact,
                -> when (other) {
                is UnionEtsTypeFact -> intersect(other, type)
                is IntersectionEtsTypeFact -> intersect(other, type)
                is GuardedTypeFact -> intersect(other, type)
                else -> null
            }

            is FunctionEtsTypeFact -> when (other) {
                is ObjectEtsTypeFact -> mkIntersectionType(type, other)
                is UnionEtsTypeFact -> intersect(other, type)
                is IntersectionEtsTypeFact -> intersect(other, type)
                is GuardedTypeFact -> intersect(other, type)
                else -> null
            }

            is ArrayEtsTypeFact -> when (other) {
                is ArrayEtsTypeFact -> {
                    val t = intersect(type.elementType, other.elementType)
                    if (t == null) {
                        logger.warn {
                            "Empty intersection of array element types: ${
                                type.elementType.toStringLimited()
                            } & ${
                                other.elementType.toStringLimited()
                            }"
                        }
                        null
                    } else {
                        ArrayEtsTypeFact(t)
                    }
                }

                else -> null
            }

            is ObjectEtsTypeFact -> when (other) {
                is ObjectEtsTypeFact -> intersect(type, other)
                is StringEtsTypeFact -> intersect(type, other)
                is FunctionEtsTypeFact -> mkIntersectionType(type, other)
                is UnionEtsTypeFact -> intersect(other, type)
                is IntersectionEtsTypeFact -> intersect(other, type)
                is GuardedTypeFact -> intersect(other, type)
                else -> null
            }

            is UnionEtsTypeFact -> intersect(type, other)
            is IntersectionEtsTypeFact -> intersect(type, other)
            is GuardedTypeFact -> intersect(type, other)
        }
    }

    private fun intersect(unionType: UnionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
        // todo: push intersection
        return mkIntersectionType(unionType, other)
    }

    private fun intersect(intersectionType: IntersectionEtsTypeFact, other: EtsTypeFact): EtsTypeFact? {
        val result = hashSetOf<EtsTypeFact>()
        for (type in intersectionType.types) {
            val intersection = intersect(type, other) ?: return null
            if (intersection is IntersectionEtsTypeFact) {
                result.addAll(intersection.types)
            } else {
                result.add(intersection)
            }
        }
        return mkIntersectionType(result)
    }

    private fun intersect(guardedType: GuardedTypeFact, other: EtsTypeFact): EtsTypeFact? {
        if (other is GuardedTypeFact) {
            if (other.guard == guardedType.guard) {
                return if (other.guardNegated == guardedType.guardNegated) {
                    intersect(guardedType.type, other.type)?.withGuard(guardedType.guard, guardedType.guardNegated)
                } else {
                    union(guardedType.type, other.type)
                }
            }
        }

        // todo: evaluate types
        return mkIntersectionType(guardedType, other)
    }

    private fun tryIntersect(cls1: EtsType?, cls2: EtsType?): EtsType? {
        if (cls1 == cls2) return cls1
        if (cls1 == null) return cls2
        if (cls2 == null) return cls1
        // TODO: isSubtype
        return null
    }

    private fun intersect(obj1: ObjectEtsTypeFact, obj2: ObjectEtsTypeFact): EtsTypeFact? {
        val intersectionProperties = obj1.getRealProperties().toMutableMap()
        for ((property, type) in obj2.getRealProperties()) {
            val currentType = intersectionProperties[property]
            if (currentType == null) {
                intersectionProperties[property] = type
            } else {
                intersectionProperties[property] = intersect(currentType, type)
                    ?: return null
            }
        }
        val intersectionCls = tryIntersect(obj1.cls, obj2.cls)
        return ObjectEtsTypeFact(intersectionCls, intersectionProperties)
    }

    private fun intersect(obj: ObjectEtsTypeFact, string: StringEtsTypeFact): EtsTypeFact? {
        if (obj.cls == EtsStringType) return string
        if (obj.cls != null) return null

        val intersectionProperties = obj.properties
            .filter { it.key in EtsTypeFact.allStringProperties }
            .mapValues { (_, type) ->
                // TODO: intersect with the corresponding type of String's property
                type
            }

        return ObjectEtsTypeFact(null, intersectionProperties)
    }

    private fun union(unionType: UnionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
        val result = hashSetOf<EtsTypeFact>()
        for (type in unionType.types) {
            val union = union(type, other)
            if (union is UnionEtsTypeFact) {
                result.addAll(union.types)
            } else {
                result.add(union)
            }
        }
        return mkUnionType(result)
    }

    private fun union(guardedType: GuardedTypeFact, other: EtsTypeFact): EtsTypeFact {
        // todo: evaluate types
        return mkUnionType(guardedType, other)
    }

    private fun union(intersectionType: IntersectionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
        // todo: push union
        return mkUnionType(intersectionType, other)
    }

    private fun union(obj1: ObjectEtsTypeFact, obj2: ObjectEtsTypeFact): EtsTypeFact {
        if (obj1.cls != null && obj2.cls != null && obj1.cls != obj2.cls) {
            return mkUnionType(obj1, obj2)
        }

        val commonProperties = obj1.properties.keys.intersect(obj2.properties.keys).associateWith { property ->
            val thisType = obj1.properties.getValue(property)
            val otherType = obj2.properties.getValue(property)
            union(thisType, otherType)
        }

        val o1OnlyProperties = obj1.properties.filter { it.key !in obj2.properties }
        val o2OnlyProperties = obj2.properties.filter { it.key !in obj1.properties }

        val o1 = ObjectEtsTypeFact(obj1.cls, o1OnlyProperties)
        val o2 = ObjectEtsTypeFact(obj2.cls, o2OnlyProperties)

        if (commonProperties.isEmpty()) {
            return mkUnionType(o1, o2)
        }

        val commonCls = obj1.cls.takeIf { it == obj2.cls }
        val commonObject = ObjectEtsTypeFact(commonCls, commonProperties)

        if (o1OnlyProperties.isEmpty() && o2OnlyProperties.isEmpty()) {
            return commonObject
        }

        return mkIntersectionType(commonObject, mkUnionType(o1, o2))
    }

    private fun union(obj: ObjectEtsTypeFact, string: StringEtsTypeFact): EtsTypeFact {
        if (obj.cls == EtsStringType) return string
        if (obj.cls != null) return mkUnionType(obj, string)

        for (p in obj.properties.keys) {
            if (p !in EtsTypeFact.allStringProperties) {
                return mkUnionType(obj, string)
            }
        }

        return string
    }

    private fun ObjectEtsTypeFact.getRealProperties(): Map<String, EtsTypeFact> {
        if (cls == null || cls !is EtsClassType) {
            return properties
        }
        val clazz = classBySignature[cls.signature]
            ?: return properties
        val props = properties.toMutableMap()
        clazz.methods.forEach { m ->
            props.merge(m.name, FunctionEtsTypeFact) { old, new ->
                intersect(old, new).also {
                    if (it == null) {
                        logger.warn {
                            "Empty intersection: ${
                                old.toStringLimited()
                            } & ${
                                new.toStringLimited()
                            }"
                        }
                    }
                }
            }
        }
        return props
    }
}
