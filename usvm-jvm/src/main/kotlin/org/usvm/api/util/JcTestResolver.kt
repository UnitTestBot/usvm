package org.usvm.api.util

import org.jacodb.api.JcTypedMethod
import org.usvm.UConcreteHeapRef
import org.usvm.api.JcTest
import org.usvm.machine.state.JcState

interface JcTestResolver {
    fun resolve(method: JcTypedMethod, state: JcState, stringConstants: Map<String, UConcreteHeapRef>): JcTest
}
