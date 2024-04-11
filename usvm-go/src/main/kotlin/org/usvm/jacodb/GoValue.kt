package org.usvm.jacodb

import org.jacodb.api.core.cfg.CoreValue

interface GoValue : GoExpr, CoreValue<GoValue, GoType>
