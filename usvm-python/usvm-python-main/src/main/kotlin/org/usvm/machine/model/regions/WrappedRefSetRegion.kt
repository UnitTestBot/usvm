package org.usvm.machine.model.regions

import org.usvm.*
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.language.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.UPythonContext
import org.usvm.machine.symbolicobjects.PreallocatedObjects
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.utils.getMembersFromType
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UTypeModel
import org.usvm.types.first

class WrappedRefSetRegion<SetType>(
    private val ctx: UPythonContext,
    private val region: UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>,
    private val keys: Set<UConcreteHeapRef>,
    private val typeSystem: PythonTypeSystem,
    private val preallocatedObjects: PreallocatedObjects,
    private val types: UTypeModel<PythonType>,
    private val isRegionForObjectDict: Boolean
): UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {
    override fun read(key: URefSetEntryLValue<SetType>): UExpr<UBoolSort> {
        if (!isAllocatedConcreteHeapRef(key.setRef) && key.setElement !in keys) {
            if (isRegionForObjectDict && typeSystem is PythonTypeSystemWithMypyInfo) {
                val ref = key.setRef as? UConcreteHeapRef
                    ?: return ctx.falseExpr
                val type = types.typeStream(ref).first() as? ConcretePythonType
                    ?: return ctx.falseExpr
                val members = getMembersFromType(type, typeSystem)
                val str = preallocatedObjects.concreteString(UninterpretedSymbolicPythonObject(key.setElement, typeSystem))
                    ?: return ctx.falseExpr
                return if (str in members) {
                    ctx.trueExpr
                } else {
                    ctx.falseExpr
                }
            }
            return ctx.falseExpr
        }
        return region.read(key)
    }
}