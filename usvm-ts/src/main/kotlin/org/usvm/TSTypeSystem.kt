package org.usvm

import org.jacodb.ets.base.EtsPrimitiveType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsFile
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class TSTypeSystem(
    override val typeOperationsTimeout: Duration,
    val project: EtsFile
) : UTypeSystem<EtsType> {

    override fun isSupertype(supertype: EtsType, type: EtsType): Boolean {
       if (supertype == type) return true

        if (type is EtsPrimitiveType) return false
    }

    override fun hasCommonSubtype(type: EtsType, types: Collection<EtsType>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isFinal(type: EtsType): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInstantiable(type: EtsType): Boolean {
        TODO("Not yet implemented")
    }

    override fun findSubtypes(type: EtsType): Sequence<EtsType> {
        TODO("Not yet implemented")
    }

    override fun topTypeStream(): UTypeStream<EtsType> {
        TODO("Not yet implemented")
    }
}
