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
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsScene
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
    fun union(a: EtsTypeFact, b: EtsTypeFact): EtsTypeFact {
        return a.union(b)
    }

    fun intersect(a: EtsTypeFact, b: EtsTypeFact): EtsTypeFact? {
        return a.intersect(b)
    }

    @JvmName("union_")
    private fun EtsTypeFact.union(other: EtsTypeFact): EtsTypeFact {
        if (this == other) return this

        return when {
            this is ObjectEtsTypeFact && other is ObjectEtsTypeFact -> union(this, other)
            this is ObjectEtsTypeFact && other is StringEtsTypeFact -> union(this, other)
            this is UnionEtsTypeFact -> union(this, other)
            this is IntersectionEtsTypeFact -> union(this, other)
            this is GuardedTypeFact -> union(this, other)
            other is UnionEtsTypeFact -> union(other, this)
            other is IntersectionEtsTypeFact -> union(other, this)
            other is GuardedTypeFact -> union(other, this)
            else -> mkUnionType(this, other)
        }
    }

    @JvmName("intersect_")
    private fun EtsTypeFact.intersect(other: EtsTypeFact?): EtsTypeFact? {
        if (other == null) return this

        if (this == other) return this

        if (other is UnknownEtsTypeFact) return this
        if (other is AnyEtsTypeFact) return other

        return when (this) {
            is UnknownEtsTypeFact -> other

            is AnyEtsTypeFact -> this

            is StringEtsTypeFact,
            is NumberEtsTypeFact,
            is BooleanEtsTypeFact,
            is NullEtsTypeFact,
            is UndefinedEtsTypeFact,
                -> when (other) {
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                is GuardedTypeFact -> intersect(other, this)
                else -> null
            }

            is FunctionEtsTypeFact -> when (other) {
                is ObjectEtsTypeFact -> mkIntersectionType(this, other)
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                is GuardedTypeFact -> intersect(other, this)
                else -> null
            }

            is ArrayEtsTypeFact -> when (other) {
                is ArrayEtsTypeFact -> {
                    val t = elementType.intersect(other.elementType)
                    if (t == null) {
                        logger.warn {
                            "Empty intersection of array element types: ${
                                elementType.toStringLimited()
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
                is ObjectEtsTypeFact -> intersect(this, other)
                is StringEtsTypeFact -> intersect(this, other)
                is FunctionEtsTypeFact -> mkIntersectionType(this, other)
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                is GuardedTypeFact -> intersect(other, this)
                else -> null
            }

            is UnionEtsTypeFact -> intersect(this, other)
            is IntersectionEtsTypeFact -> intersect(this, other)
            is GuardedTypeFact -> intersect(this, other)
        }
    }

    private fun intersect(unionType: UnionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
        // todo: push intersection
        return mkIntersectionType(unionType, other)
    }

    private fun intersect(intersectionType: IntersectionEtsTypeFact, other: EtsTypeFact): EtsTypeFact? {
        val result = hashSetOf<EtsTypeFact>()
        for (type in intersectionType.types) {
            val intersection = type.intersect(other) ?: return null
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
                    guardedType.type.intersect(other.type)?.withGuard(guardedType.guard, guardedType.guardNegated)
                } else {
                    guardedType.type.union(other.type)
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
        val intersectionProperties = obj1.getRealProperties(scene).toMutableMap()
        for ((property, type) in obj2.getRealProperties(scene)) {
            val currentType = intersectionProperties[property]
            if (currentType == null) {
                intersectionProperties[property] = type
            } else {
                intersectionProperties[property] = currentType.intersect(type)
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
            val union = type.union(other)
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
            thisType.union(otherType)
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

    fun ObjectEtsTypeFact.getRealProperties(scene: EtsScene): Map<String, EtsTypeFact> {
        if (cls == null || cls !is EtsClassType) {
            return properties
        }
        val clazz = scene.projectAndSdkClasses.firstOrNull { it.signature == cls.signature }
            ?: return properties
        val props = properties.toMutableMap()
        clazz.methods.forEach { m ->
            props.merge(m.name, FunctionEtsTypeFact) { old, new ->
                old.intersect(new).also {
                    if (it == null) logger.warn { "Empty intersection: ${old.toStringLimited()} & ${new.toStringLimited()}" }
                }
            }
        }
        return props
    }
}
