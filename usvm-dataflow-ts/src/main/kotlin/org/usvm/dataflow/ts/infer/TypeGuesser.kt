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

import mu.KotlinLogging
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene

private val logger = KotlinLogging.logger {}

fun guessTypes(
    scene: EtsScene,
    facts: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
    allowResolvedAlternatives: Boolean,
): Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>> {
    return facts.mapValues { (method, types) ->
        if (types.isEmpty()) {
            logger.warn { "Facts are empty for method ${method.signature}" }
            return@mapValues types
        }

        val updatedTypes = types.mapValues { (accessPath, fact) ->
            logger.info {
                "Resolving a type for a fact \"$fact\" for access path \"$accessPath\" in the method \"$method\""
            }

            val resultingType = fact.resolveType(scene)
            logger.info { "The result is $resultingType" }

            resultingType
        }

        updatedTypes
    }
}

fun EtsTypeFact.resolveType(
    scene: EtsScene,
): EtsTypeFact {
    val simplifiedFact = simplify()

    return when (simplifiedFact) {
        is EtsTypeFact.ArrayEtsTypeFact -> simplifiedFact.resolveArrayTypeFact(scene)
        is EtsTypeFact.ObjectEtsTypeFact -> simplifiedFact.resolveObjectTypeFact(scene)
        is EtsTypeFact.FunctionEtsTypeFact -> simplifiedFact
        is EtsTypeFact.GuardedTypeFact -> TODO("guarded")
        is EtsTypeFact.IntersectionEtsTypeFact -> {
            val updatedTypes = simplifiedFact.types.mapTo(hashSetOf()) { it.resolveType(scene) }
            EtsTypeFact.mkIntersectionType(updatedTypes).simplify()
        }

        is EtsTypeFact.UnionEtsTypeFact -> {
            val updatedTypes = simplifiedFact.types.mapNotNullTo(mutableSetOf()) { type ->
                val resolvedType = type.resolveType(scene)
                resolvedType.takeIf { it !is EtsTypeFact.AnyEtsTypeFact }
            }

            if (updatedTypes.isEmpty()) {
                EtsTypeFact.AnyEtsTypeFact
            } else {
                EtsTypeFact.mkUnionType(updatedTypes).simplify()
            }
        }

        else -> simplify()
    }
}

private fun EtsTypeFact.ObjectEtsTypeFact.resolveObjectTypeFact(
    scene: EtsScene,
): EtsTypeFact {
    if (cls != null) {
        return this
    }

    val touchedPropertiesNames = properties.keys
    val classesInSystem = scene
        .classes
        .filter { cls ->
            val propertiesSet = mutableSetOf<String>()
            cls.methods.mapTo(propertiesSet) { it.name }
            cls.fields.mapTo(propertiesSet) { it.name }

            touchedPropertiesNames.all { name -> name in propertiesSet }
        }

    if (classesInSystem.isEmpty()) {
        val indicesProperties = properties.filter { (k, _) -> k.toIntOrNull() != null }
        if (indicesProperties.isNotEmpty()) {
            val elementTypeFacts = indicesProperties.mapTo(hashSetOf()) {
                it.value.resolveType(scene)
            }

            val typeFact = EtsTypeFact.mkUnionType(elementTypeFacts).simplify()

            return EtsTypeFact.ArrayEtsTypeFact(typeFact)
        }

        if ("length" in touchedPropertiesNames && "splice" in touchedPropertiesNames) {
            return EtsTypeFact.ArrayEtsTypeFact(EtsTypeFact.AnyEtsTypeFact)
        }

        return this
    }

    val suitableTypes = classesInSystem
        .mapTo(hashSetOf()) { cls ->
            EtsTypeFact.ObjectEtsTypeFact(
                cls = EtsClassType(signature = cls.signature),
                properties = properties.mapValues {
                    it.value.resolveType(scene)
                }
            )
        }

    // TODO process arrays here (and strings)

    return when {
        suitableTypes.isEmpty() -> error("Should be processed earlier")
        suitableTypes.size == 1 -> suitableTypes.single()
        suitableTypes.size in 2..5 -> EtsTypeFact.mkUnionType(suitableTypes).simplify()
        else -> this
    }
}

private fun EtsTypeFact.ArrayEtsTypeFact.resolveArrayTypeFact(
    scene: EtsScene,
): EtsTypeFact.ArrayEtsTypeFact {
    return if (elementType is EtsTypeFact.UnknownEtsTypeFact) {
        this
    } else {
        val updatedElementType = elementType.resolveType(scene)

        if (updatedElementType === elementType) this else copy(elementType = elementType)
    }
}

fun EtsTypeFact.simplify(): EtsTypeFact {
    return when (this) {
        is EtsTypeFact.UnionEtsTypeFact -> simplifyUnionTypeFact()
        is EtsTypeFact.IntersectionEtsTypeFact -> simplifyIntersectionTypeFact()
        is EtsTypeFact.GuardedTypeFact -> TODO("Guarded type facts are unsupported in simplification")
        is EtsTypeFact.ArrayEtsTypeFact -> {
            val elementType = elementType.simplify()

            if (elementType === this.elementType) this else copy(elementType = elementType)
        }

        is EtsTypeFact.ObjectEtsTypeFact -> {
            if (cls != null) {
                return this
            }

            val props = properties.mapValues { it.value.simplify() }
            copy(properties = props)
        }

        else -> this
    }
}

private fun EtsTypeFact.IntersectionEtsTypeFact.simplifyIntersectionTypeFact(): EtsTypeFact {
    val simplifiedArgs = types.map { it.simplify() }

    simplifiedArgs.singleOrNull()?.let { return it }

    val updatedTypeFacts = hashSetOf<EtsTypeFact>()

    val (objectClassFacts, otherFacts) = simplifiedArgs.partition {
        it is EtsTypeFact.ObjectEtsTypeFact && it.cls == null
    }

    updatedTypeFacts.addAll(otherFacts)

    if (objectClassFacts.isNotEmpty()) {
        val allProperties = hashMapOf<String, MutableSet<EtsTypeFact>>().withDefault { hashSetOf() }

        objectClassFacts.forEach { fact ->
            fact as EtsTypeFact.ObjectEtsTypeFact

            fact.properties.forEach { (name, propertyFact) ->
                allProperties.getValue(name).add(propertyFact)
            }
        }

        val mergedAllProperties = hashMapOf<String, EtsTypeFact>()
        allProperties.forEach { (name, propertyFact) ->
            mergedAllProperties[name] = EtsTypeFact.mkUnionType(propertyFact)
        }

        updatedTypeFacts += EtsTypeFact.ObjectEtsTypeFact(cls = null, properties = mergedAllProperties)
    }

    return EtsTypeFact.mkIntersectionType(updatedTypeFacts)
}

private fun EtsTypeFact.UnionEtsTypeFact.simplifyUnionTypeFact(): EtsTypeFact {
    val simplifiedArgs = types.map { it.simplify() }

    simplifiedArgs.singleOrNull()?.let { return it }

    val updatedTypeFacts = hashSetOf<EtsTypeFact>()

    var atLeastOneNonEmptyObjectFound = false
    var emptyTypeObjectFact: EtsTypeFact? = null

    simplifiedArgs.forEach {
        if (it !is EtsTypeFact.ObjectEtsTypeFact) {
            updatedTypeFacts += it
            return@forEach
        }

        if (it.cls != null) {
            atLeastOneNonEmptyObjectFound = true
            updatedTypeFacts += it
            return@forEach
        }

        if (it.properties.isEmpty() && emptyTypeObjectFact == null) {
            emptyTypeObjectFact = it
        } else {
            updatedTypeFacts += it
            atLeastOneNonEmptyObjectFound = true
        }
    }

    // take a fact `Object {}` only if there were no other objects in the facts
    emptyTypeObjectFact?.let {
        if (!atLeastOneNonEmptyObjectFound) {
            updatedTypeFacts += it
        }
    }

    return EtsTypeFact.mkUnionType(updatedTypeFacts)
}
