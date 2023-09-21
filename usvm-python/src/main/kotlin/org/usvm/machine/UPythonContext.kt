package org.usvm.machine

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import org.usvm.*
import org.usvm.language.types.PythonTypeSystem

class UPythonContext(typeSystem: PythonTypeSystem): UContext(PythonComponents(typeSystem)) {
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
        //return mkRealToFpExpr(fp64Sort, floatRoundingMode, realValue)
    }
}