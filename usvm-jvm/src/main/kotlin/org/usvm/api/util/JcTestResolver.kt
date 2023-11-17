package org.usvm.api.util

import org.jacodb.api.JcTypedMethod
import org.usvm.UConcreteHeapAddress
import org.usvm.api.JcTest
import org.usvm.machine.state.JcState

interface JcTestResolver {
    fun resolve(method: JcTypedMethod, state: JcState): JcTest

    companion object {
        @PublishedApi
        internal val CYCLIC_REF_STUB = Any()

        inline fun <T> MutableMap<UConcreteHeapAddress, T>.resolveRef(
            ref: UConcreteHeapAddress,
            resolve: () -> T
        ): T {
            val result = this[ref]
            if (result != null) {
                if (result === CYCLIC_REF_STUB) {
                    error("Cyclic reference occurred when resolving: $ref")
                }

                return result
            }

            try {
                @Suppress("UNCHECKED_CAST")
                this[ref] = CYCLIC_REF_STUB as T

                return resolve()
            } finally {
                val current = this[ref]
                // Ref resolution process finished without producing a result
                if (current === CYCLIC_REF_STUB) {
                    remove(ref)
                }
            }
        }
    }
}
