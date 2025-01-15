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

package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsScene

class TypeGuesser(
    private val scene: EtsScene,
) {
    private val propertyNameToClasses: Map<String, Set<EtsClass>> by lazy {
        val result: MutableMap<String, MutableSet<EtsClass>> = hashMapOf()
        scene.projectAndSdkClasses.forEach { clazz ->
            clazz.methods.forEach {
                result.computeIfAbsent(it.name) { hashSetOf() }.add(clazz)
            }
            clazz.fields.forEach {
                result.computeIfAbsent(it.name) { hashSetOf() }.add(clazz)
            }
        }
        result
    }

    fun guess(fact: EtsTypeFact): EtsTypeFact {
        return fact.resolveType()
    }

    private fun EtsTypeFact.resolveType(): EtsTypeFact =
        when (val simplifiedFact = simplify()) {
            is EtsTypeFact.ArrayEtsTypeFact -> simplifiedFact.resolveArrayTypeFact()
            is EtsTypeFact.ObjectEtsTypeFact -> simplifiedFact.resolveObjectTypeFact()
            is EtsTypeFact.FunctionEtsTypeFact -> simplifiedFact
            is EtsTypeFact.GuardedTypeFact -> TODO("guarded")

            is EtsTypeFact.IntersectionEtsTypeFact -> {
                val updatedTypes = simplifiedFact.types.mapTo(hashSetOf()) {
                    it.resolveType()
                }
                EtsTypeFact.mkIntersectionType(updatedTypes).simplify()
            }

            is EtsTypeFact.UnionEtsTypeFact -> {
                val updatedTypes = simplifiedFact.types.mapNotNullTo(hashSetOf()) {
                    val resolved = it.resolveType()
                    if (resolved is EtsTypeFact.AnyEtsTypeFact) {
                        null
                    } else {
                        resolved
                    }
                }

                if (updatedTypes.isEmpty()) {
                    EtsTypeFact.AnyEtsTypeFact
                } else {
                    EtsTypeFact.mkUnionType(updatedTypes).simplify()
                }
            }

            else -> simplify()
        }

    private fun EtsTypeFact.ObjectEtsTypeFact.resolveObjectTypeFact(): EtsTypeFact {
        if (cls != null) {
            // TODO: inspect this.properties and maybe find more exact class-type than 'cls'
            return this
        }

        val touchedPropertiesNames = properties.keys
        val classesInSystem = collectSuitableClasses(touchedPropertiesNames)

        if (classesInSystem.isEmpty()) {
            return tryToDetermineSpecialObjects(touchedPropertiesNames)
        }

        val suitableTypes = resolveTypesFromClasses(classesInSystem)

        // TODO process arrays here (and strings)

        return when {
            suitableTypes.isEmpty() -> error("Should be processed earlier")
            suitableTypes.size == 1 -> suitableTypes.single()
            suitableTypes.size in 2..5 -> EtsTypeFact.mkUnionType(suitableTypes).simplify()
            else -> this
        }
    }

    private fun EtsTypeFact.ObjectEtsTypeFact.resolveTypesFromClasses(
        classes: Iterable<EtsClass>,
    ): Set<EtsTypeFact.ObjectEtsTypeFact> =
        classes.mapTo(hashSetOf()) { cls ->
            EtsTypeFact.ObjectEtsTypeFact(
                cls = EtsClassType(signature = cls.signature),
                properties = properties.mapValues {
                    it.value.resolveType()
                }
            )
        }

    private fun collectSuitableClasses(
        touchedPropertiesNames: Set<String>,
    ): Set<EtsClass> {
        val classesWithProperties = touchedPropertiesNames.map { propertyNameToClasses[it] ?: emptySet() }
        return classesWithProperties.reduceOrNull { a, b -> a intersect b } ?: emptySet()
    }

    private fun EtsTypeFact.ObjectEtsTypeFact.tryToDetermineSpecialObjects(
        touchedPropertiesNames: Set<String>,
    ): EtsTypeFact.BasicType {
        val indicesProperties = properties.filter { (k, _) -> k.toIntOrNull() != null }
        if (indicesProperties.isNotEmpty()) {
            val elementTypeFacts = indicesProperties.mapTo(hashSetOf()) {
                it.value.resolveType()
            }

            val typeFact = EtsTypeFact.mkUnionType(elementTypeFacts).simplify()

            return EtsTypeFact.ArrayEtsTypeFact(typeFact)
        }

        if ("length" in touchedPropertiesNames && "splice" in touchedPropertiesNames) {
            return EtsTypeFact.ArrayEtsTypeFact(EtsTypeFact.AnyEtsTypeFact)
        }

        return this
    }

    private fun EtsTypeFact.ArrayEtsTypeFact.resolveArrayTypeFact(): EtsTypeFact.ArrayEtsTypeFact =
        if (elementType is EtsTypeFact.UnknownEtsTypeFact) {
            this
        } else {
            val updatedElementType = elementType.resolveType()
            if (updatedElementType === elementType) this else copy(elementType = elementType)
        }
}
