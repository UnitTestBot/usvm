package org.usvm.machine.call.intrinsic

import org.usvm.machine.call.TsUnknownCallModelImplementationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TsIntrinsicUnknownCallModelTest {
    @Test
    fun `array pop registration binds the intrinsic backend`() {
        val registration = TsArrayPopIntrinsicModel.registration

        assertEquals(expected = "ts.array.pop", actual = registration.descriptor.id)
        assertEquals(
            expected = TsUnknownCallModelImplementationKind.INTRINSIC,
            actual = registration.descriptor.implementationKind,
        )
        assertIs<TsIntrinsicUnknownCallModelImplementation>(registration.implementation)
    }
}
