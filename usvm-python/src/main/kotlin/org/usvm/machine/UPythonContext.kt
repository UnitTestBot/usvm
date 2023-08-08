package org.usvm.machine

import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext

class UPythonContext: UContext(PythonComponents) {
    private var nextAddress: UConcreteHeapAddress = -1000_000_000
    fun provideRawConcreteHeapRef(): UConcreteHeapRef {
        return mkConcreteHeapRef(nextAddress--)
    }
}