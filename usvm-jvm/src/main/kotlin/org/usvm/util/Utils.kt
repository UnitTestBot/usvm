package org.usvm.util

import org.jacodb.api.JcType
import org.usvm.machine.JcContext
import kotlin.reflect.KClass

fun JcContext.extractJcType(clazz: KClass<*>): JcType = cp.findTypeOrNull(clazz.qualifiedName!!)!!