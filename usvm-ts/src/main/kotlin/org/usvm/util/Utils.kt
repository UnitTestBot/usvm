package org.usvm.util

import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsScene
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.machine.TSContext

// Built-in KContext.bvToBool has identical implementation.
fun TSContext.boolToFp(expr: UExpr<UBoolSort>): UExpr<KFp64Sort> =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))


// TODO probably this should be written differently
fun EtsScene.fieldLookUp(field: EtsFieldSignature) = projectAndSdkClasses
    .first { it.signature == field.enclosingClass }
    .fields
    .single { it.name == field.name }