package org.usvm.util

import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.usvm.TSTest
import org.usvm.state.TSState

class TSTestResolver {

    fun resolve(method: EtsMethod, state: TSState): TSTest {

        return TSTest(emptyList(), null)
    }
}
