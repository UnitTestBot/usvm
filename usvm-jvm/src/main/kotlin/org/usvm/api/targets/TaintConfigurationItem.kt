package org.usvm.api.targets

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod

// TODO separate cleaning actions from the ones who can taint data
data class TaintConfiguration(
    val entryPoints: Map<JcMethod, List<TaintEntryPointSource>>,
    val methodSources: Map<JcMethod, List<TaintMethodSource>>,
    val fieldSources: Map<JcField, List<TaintFieldSource>>,
    val passThrough: Map<JcMethod, List<TaintPassThrough>>,
    val cleaners: Map<JcMethod, List<TaintCleaner>>,
    val methodSinks: Map<JcMethod, List<TaintMethodSink>>,
    val fieldSinks: Map<JcField, List<TaintFieldSink>>,
)

sealed interface TaintConfigurationItem

class TaintEntryPointSource(
    val method: JcMethod,
    val condition: Condition,
    val action: Action,
) : TaintConfigurationItem

class TaintMethodSource(
    val method: JcMethod,
    val condition: Condition,
    val action: Action,
) : TaintConfigurationItem

class TaintFieldSource(
    val field: JcField,
    val condition: Condition,
    val action: Action,
) : TaintConfigurationItem

class TaintMethodSink(
    val condition: Condition,
    val method: JcMethod,
) : TaintConfigurationItem

class TaintFieldSink(
    val condition: Condition,
    val field: JcField,
) : TaintConfigurationItem

class TaintPassThrough(
    val methodInfo: JcMethod,
    val condition: Condition,
    val action: Action,
) : TaintConfigurationItem

class TaintCleaner(
    val methodInfo: JcMethod,
    val condition: Condition,
    val action: Action,
) : TaintConfigurationItem

