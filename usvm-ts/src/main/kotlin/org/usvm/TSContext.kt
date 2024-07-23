package org.usvm

import io.ksmt.sort.KFp64Sort

typealias TSSizeSort = UBv32Sort
typealias TSNumberSort = KFp64Sort

class TSContext(components: TSComponents) : UContext<TSSizeSort>(components)
