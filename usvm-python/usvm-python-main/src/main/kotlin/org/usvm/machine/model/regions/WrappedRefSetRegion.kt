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
): UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {
    override fun read(key: URefSetEntryLValue<SetType>): UExpr<UBoolSort> {
        if (!isAllocatedConcreteHeapRef(key.setRef) && key.setElement !in keys) {
            return ctx.falseExpr
        }
        return region.read(key)
    }
}