package org.usvm.language

inline val Method<*>.arity get() = argumentsTypes.size
inline val Method<*>.registersCount get() = body?.registersCount ?: arity