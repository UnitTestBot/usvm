package org.usvm.machine

import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.language.types.PythonTypeSystem

class UPythonContext(typeSystem: PythonTypeSystem): UContext(PythonComponents(typeSystem)) {
    private var nextAddress: UConcreteHeapAddress = -1_000_000_000
    fun provideRawConcreteHeapRef(): UConcreteHeapRef {
        return mkConcreteHeapRef(nextAddress--)
    }
}