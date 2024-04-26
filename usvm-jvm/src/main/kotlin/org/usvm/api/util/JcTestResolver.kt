package org.usvm.api.util

import org.jacodb.api.jvm.JcTypedMethod
import org.usvm.api.JcTest
import org.usvm.machine.state.JcState

interface JcTestResolver {
    fun resolve(method: JcTypedMethod, state: JcState): JcTest
}
