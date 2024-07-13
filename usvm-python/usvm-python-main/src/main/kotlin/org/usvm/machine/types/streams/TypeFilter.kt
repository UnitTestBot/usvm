package org.usvm.machine.types.streams

import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem


class TypeFilter(
    private val typeSystem: PythonTypeSystem,
    private val supertypes: Set<PythonType>,
    private val notSupertypes: Set<PythonType>,
    private val subtypes: Set<PythonType>,
    private val notSubtypes: Set<PythonType>,
) {
    fun addSupertype(type: PythonType): TypeFilter =
        TypeFilter(typeSystem, supertypes + type, notSupertypes, subtypes, notSubtypes)

    fun addNotSupertype(type: PythonType): TypeFilter =
        TypeFilter(typeSystem, supertypes, notSupertypes + type, subtypes, notSubtypes)

    fun addSubtype(type: PythonType): TypeFilter =
        TypeFilter(typeSystem, supertypes, notSupertypes, subtypes + type, notSubtypes)

    fun addNotSubtype(type: PythonType): TypeFilter =
        TypeFilter(typeSystem, supertypes, notSupertypes, subtypes, notSubtypes + type)

    fun filterTypes(types: Sequence<PythonType>): Sequence<PythonType> =
        types.filter { type ->
            supertypes.all { typeSystem.isSupertype(it, type) } &&
                notSupertypes.all { !typeSystem.isSupertype(it, type) } &&
                subtypes.all { typeSystem.isSupertype(type, it) } &&
                notSubtypes.all { !typeSystem.isSupertype(type, it) }
        }

    companion object {
        fun empty(typeSystem: PythonTypeSystem): TypeFilter =
            TypeFilter(typeSystem, emptySet(), emptySet(), emptySet(), emptySet())
    }
}

fun Sequence<PythonType>.filter(filter: TypeFilter): Sequence<PythonType> =
    filter.filterTypes(this)
