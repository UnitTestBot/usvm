package org.usvm.machine.interpreter

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.jacodb.api.jvm.*
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId

object JcClassInterningRegionId : UMemoryRegionId<Nothing, Nothing> {
    override val sort: Nothing
        get() = throw IllegalStateException("JcClassInterningRegionId.sort is unreachable")

    override fun emptyRegion(): UMemoryRegion<Nothing, Nothing> = JcClassInterningRegion()
}

private fun resolveTypeInfo(type: JcType): JcTypeInfo = when (type) {
    is JcClassType -> JcClassTypeInfo(type.jcClass)
    is JcPrimitiveType -> JcPrimitiveTypeInfo(type)
    is JcArrayType -> JcArrayTypeInfo(resolveTypeInfo(type.elementType))
    else -> error("Unexpected type: $type")
}

internal sealed interface JcTypeInfo

private data class JcClassTypeInfo(val className: String) : JcTypeInfo {
    // Don't use type.typeName here, because it contains generic parameters
    constructor(cls: JcClassOrInterface) : this(cls.name)
}

private data class JcPrimitiveTypeInfo(val type: JcPrimitiveType) : JcTypeInfo

private data class JcArrayTypeInfo(val element: JcTypeInfo) : JcTypeInfo

internal class JcClassInterningRegion(
    private val interningPool: PersistentMap<JcTypeInfo, UConcreteHeapRef> = persistentMapOf()
): UMemoryRegion<Nothing, Nothing> {

    fun getOrPut(jcType: JcType, defaultValue: () -> UConcreteHeapRef): Pair<UConcreteHeapRef, JcClassInterningRegion> {
        val typeInfo = resolveTypeInfo(jcType)
        val address = interningPool[typeInfo]
        if (address != null)
            return address to this

        val newAddress = defaultValue()
        val newPool = interningPool.put(typeInfo, newAddress)
        return newAddress to JcClassInterningRegion(newPool)
    }

    override fun read(key: Nothing): UExpr<Nothing> {
        throw IllegalStateException("JcClassInterningRegion.read is unreachable")
    }

    override fun write(key: Nothing, value: UExpr<Nothing>, guard: UBoolExpr, ownership: MutabilityOwnership): UMemoryRegion<Nothing, Nothing> {
        throw IllegalStateException("JcClassInterningRegion.write is unreachable")
    }
}
