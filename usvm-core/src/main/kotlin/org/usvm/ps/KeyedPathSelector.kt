package org.usvm.ps

import org.usvm.UPathSelector

/**
 * [UPathSelector] modification which allows grouping states by generic keys.
 */
interface KeyedPathSelector<State, Key> : UPathSelector<State> {

    fun removeKey(key: Key)
}
