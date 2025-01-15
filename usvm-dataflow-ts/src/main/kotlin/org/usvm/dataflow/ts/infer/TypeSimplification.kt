package org.usvm.dataflow.ts.infer

fun EtsTypeFact.simplify(): EtsTypeFact = when (this) {
    is EtsTypeFact.UnionEtsTypeFact -> simplifyUnionTypeFact()
    is EtsTypeFact.IntersectionEtsTypeFact -> simplifyIntersectionTypeFact()
    is EtsTypeFact.GuardedTypeFact -> TODO("Guarded type facts are unsupported in simplification")

    is EtsTypeFact.ArrayEtsTypeFact -> {
        val elementType = elementType.simplify()
        if (elementType === this.elementType) {
            this
        } else {
            copy(elementType = elementType)
        }
    }

    is EtsTypeFact.ObjectEtsTypeFact -> {
        if (cls == null) {
            val properties = properties.mapValues { it.value.simplify() }
            EtsTypeFact.ObjectEtsTypeFact(cls = null, properties = properties)
        } else {
            this
        }
    }

    else -> this
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
