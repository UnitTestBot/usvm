package org.usvm.machine.types

import com.jetbrains.rd.framework.util.RdCoroutineScope.Companion.override
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.machine.types.TsTypeSystem.Companion.primitiveTypes
import org.usvm.types.TypesResult
import org.usvm.types.TypesResult.Companion.toTypesResult
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.types.emptyTypeStream
import org.usvm.util.type
import kotlin.time.Duration

// TODO this is draft, should be replaced with real implementation
class TsTypeSystem(
    override val typeOperationsTimeout: Duration,
    val project: EtsScene,
) : UTypeSystem<EtsType> {

    companion object {
        // TODO: add more primitive types (string, etc.) once supported
        val primitiveTypes = sequenceOf(EtsNumberType, EtsBooleanType)
    }

    override fun isSupertype(supertype: EtsType, type: EtsType): Boolean = when {
        type is AuxiliaryType -> TODO()
        supertype == type -> true
        supertype == EtsUnknownType || supertype == EtsAnyType -> true
        else -> TODO()
    }

    //
    override fun hasCommonSubtype(type: EtsType, types: Collection<EtsType>): Boolean = when {
        type is EtsPrimitiveType -> types.any { it == type }
        type is EtsClassType -> TODO()
        type is EtsUnclearRefType -> TODO()
        type is EtsArrayType -> TODO()
        else -> error("Unsupported class type: $type")
    }

    // TODO is it right?
    override fun isFinal(type: EtsType): Boolean = type is EtsPrimitiveType

    // TODO are there any non instantiable types?
    override fun isInstantiable(type: EtsType): Boolean = true

    override fun findSubtypes(type: EtsType): Sequence<EtsType> = when (type) {
        is EtsPrimitiveType -> emptySequence() // TODO why???
        is EtsAnyType,
        is EtsUnknownType -> project.projectAndSdkClasses.asSequence().map { it.type }
        else -> TODO()
    }

    private val topTypeStream by lazy { TsTopTypeStream(this) }

    override fun topTypeStream(): UTypeStream<EtsType> = topTypeStream
}

