package org.usvm.machine.expr

import org.jacodb.ets.base.EtsType
import org.usvm.machine.TSExprTranslator
import org.usvm.model.ULazyModelDecoder
import org.usvm.model.UModelBase

class TSModelDecoder(translator: UModelEva) : ULazyModelDecoder<UModelBase<EtsType>>(translator) {
}