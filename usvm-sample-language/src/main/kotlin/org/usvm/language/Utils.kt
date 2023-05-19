package org.usvm.language

inline val Method<*>.argumentCount get() = argumentsTypes.size
inline val Method<*>.registersCount get() = body?.registersCount ?: argumentCount
inline val Method<*>.localsCount get() = registersCount - argumentCount