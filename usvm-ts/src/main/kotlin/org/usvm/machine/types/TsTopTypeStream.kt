package org.usvm.machine.types

import com.sun.tools.javac.tree.TreeInfo.types
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.machine.types.TsTypeSystem.Companion.primitiveTypes
import org.usvm.types.TypesResult
import org.usvm.types.TypesResult.Companion.toTypesResult
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.emptyTypeStream

class TsTopTypeStream(
    private val typeSystem: TsTypeSystem,
    // We treat unknown as a top type in this system
    private val anyTypeStream: UTypeStream<EtsType> = USupportTypeStream.from(typeSystem, EtsUnknownType),
) : UTypeStream<EtsType> {

    override fun filterBySupertype(type: EtsType): UTypeStream<EtsType> {
        if (type is EtsPrimitiveType) return emptyTypeStream()

        return anyTypeStream.filterBySupertype(type)
    }

    override fun filterBySubtype(type: EtsType): UTypeStream<EtsType> {
        return anyTypeStream.filterBySubtype(type)
    }

    override fun filterByNotSupertype(type: EtsType): UTypeStream<EtsType> {
        return TsTopTypeStream(typeSystem, anyTypeStream.filterByNotSupertype(type))
    }

    override fun filterByNotSubtype(type: EtsType): UTypeStream<EtsType> {
        return TsTopTypeStream(typeSystem, anyTypeStream.filterByNotSubtype(type))
    }

    override fun take(n: Int): TypesResult<EtsType> {
        return anyTypeStream.take(n)
    }

    override val isEmpty: Boolean?
        get() = anyTypeStream.isEmpty

    override val commonSuperType: EtsType?
        get() = EtsUnknownType.takeIf { isEmpty == false }

    private fun <T> List<T>.remove(x: T): List<T> = this.filterNot { it == x }
}
