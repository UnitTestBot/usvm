package org.usvm.machine

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.machine.types.PythonTypeSystem

class PyContext(
    typeSystem: PythonTypeSystem,
    components: PyComponents = PyComponents(typeSystem),
) : UContext<KIntSort>(components) {
    private var nextAddress: UConcreteHeapAddress = INITIAL_STATIC_ADDRESS / 2
    fun provideRawConcreteHeapRef(): UConcreteHeapRef {
        require(nextAddress > INITIAL_STATIC_ADDRESS) {
            "Should not return a static ref"
        }

        return mkConcreteHeapRef(nextAddress--)
    }

    val floatRoundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven)

    fun intToFloat(intValue: UExpr<KIntSort>): UExpr<KRealSort> {
        return mkIntToReal(intValue)
    }
}
