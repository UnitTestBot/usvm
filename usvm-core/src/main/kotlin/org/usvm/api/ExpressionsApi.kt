package org.usvm.api

import org.usvm.UState

class ExpressionsApi<Type, State: UState<Type, *, *>>(state: State) {
    // TODO:
    // 1. move all relevant KSMT context functions here
    // 2. elaborate on size expressions for python?
    // 3. use path constraints to simplify make functions?
}