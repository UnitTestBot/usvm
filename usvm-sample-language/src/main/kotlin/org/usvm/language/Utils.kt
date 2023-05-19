package org.usvm.language

inline val Method<*>.argumentsCount get() = argumentsTypes.size
inline val Method<*>.registersCount get() = body?.registersCount ?: argumentsCount
inline val Method<*>.localsCount get() = registersCount - argumentsCount